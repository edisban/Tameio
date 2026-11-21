"""Variants 2 and 3: a small instruct model reads the OCR text, with or without a LoRA adapter.

Needs the optional libraries (pip install -e ".[llm]" and PyTorch); the baseline never imports this.
"""

from pathlib import Path

from extraction import prompt
from extraction.schema import parse_reply
from training.dataset import Example

BASE_MODEL = "Qwen/Qwen2.5-0.5B-Instruct"
# The JSON answer is about 90 tokens; the cap stops a model that rambles
MAX_NEW_TOKENS = 160
BYTES_PER_MB = 1_000_000


class LlmReader:
    def __init__(self, model_id: str = BASE_MODEL, adapter: Path | None = None, shown: Example | None = None):
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer

        self._torch = torch
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        dtype = torch.float16 if self.device == "cuda" else torch.float32
        self.tokenizer = AutoTokenizer.from_pretrained(model_id)
        model = AutoModelForCausalLM.from_pretrained(model_id, dtype=dtype).to(self.device)
        self._size_mb = _bytes(model) / BYTES_PER_MB
        if adapter is not None:
            from peft import PeftModel

            model = PeftModel.from_pretrained(model, str(adapter))
            self._size_mb = _bytes(model) / BYTES_PER_MB
            # Folding the adapter into the weights makes inference as fast as the base model
            model = model.merge_and_unload()
        self.model = model.eval()
        self._shown = shown
        self.name = "finetuned" if adapter else "prompted"
        self.model_name = f"{model_id} + LoRA" if adapter else model_id

    @property
    def hardware(self) -> str:
        if self.device == "cuda":
            return f"GPU {self._torch.cuda.get_device_name(0)}"
        return f"CPU, {self._torch.get_num_threads()} threads"

    def size_mb(self) -> float:
        return self._size_mb

    def reply(self, text: str) -> str:
        inputs = self.tokenizer.apply_chat_template(
            prompt.messages(text, self._shown), add_generation_prompt=True, return_tensors="pt", return_dict=True
        ).to(self.device)
        with self._torch.inference_mode():
            output = self.model.generate(
                **inputs, max_new_tokens=MAX_NEW_TOKENS, do_sample=False, pad_token_id=self.tokenizer.eos_token_id
            )
        return self.tokenizer.decode(output[0, inputs["input_ids"].shape[1] :], skip_special_tokens=True)

    def read(self, text: str) -> dict[str, str | None] | None:
        """The fields, or None when the reply is not valid JSON in the schema."""
        parsed = parse_reply(self.reply(text))
        return parsed.as_text() if parsed else None


def _bytes(model) -> int:
    return sum(p.numel() * p.element_size() for p in model.parameters())
