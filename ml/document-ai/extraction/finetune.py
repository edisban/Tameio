"""Variant 3: LoRA supervised fine-tuning (SFT) of the small model on the train split.

    python -m extraction.finetune --out models/field-extraction-lora

Meant for a GPU (the notebook runs it on a free Colab T4); it also runs on a CPU, slowly. Writes only
the adapter, a few megabytes: the base model stays as it is and is downloaded again when needed.
"""

import argparse
from pathlib import Path

from extraction import prompt
from extraction.llm import BASE_MODEL
from training.dataset import DATA_ROOT, load

SEED = 42
# LoRA: train two small matrices of rank 16 beside every linear layer, not the 500 million weights
LORA_RANK = 16
LORA_ALPHA = 32
LORA_DROPOUT = 0.05
EPOCHS = 2
LEARNING_RATE = 2e-4
BATCH_SIZE = 8
WARMUP_STEPS = 5
MAX_LENGTH = 1024


def finetune(out: Path, model_id: str = BASE_MODEL, data: Path = DATA_ROOT, epochs: int = EPOCHS) -> None:
    import torch
    from datasets import Dataset
    from peft import LoraConfig
    from trl import SFTConfig, SFTTrainer

    def rows(split: str) -> Dataset:
        return Dataset.from_list([prompt.training_example(e) for e in load(split, data)])

    gpu = torch.cuda.is_available()
    trainer = SFTTrainer(
        model=model_id,
        train_dataset=rows("train"),
        eval_dataset=rows("validation"),
        peft_config=LoraConfig(
            r=LORA_RANK,
            lora_alpha=LORA_ALPHA,
            lora_dropout=LORA_DROPOUT,
            target_modules="all-linear",
            task_type="CAUSAL_LM",
        ),
        args=SFTConfig(
            output_dir=str(out / "checkpoints"),
            num_train_epochs=epochs,
            per_device_train_batch_size=BATCH_SIZE,
            per_device_eval_batch_size=BATCH_SIZE,
            learning_rate=LEARNING_RATE,
            lr_scheduler_type="cosine",
            warmup_steps=WARMUP_STEPS,
            max_length=MAX_LENGTH,
            # The prompt-completion rows make the loss count only the answer, not the receipt text
            completion_only_loss=True,
            eval_strategy="epoch",
            save_strategy="no",
            logging_steps=10,
            # A T4 has no bfloat16, so half precision is fp16; a CPU trains in full precision
            bf16=False,
            fp16=gpu,
            use_cpu=not gpu,
            seed=SEED,
            report_to="none",
        ),
    )
    trainer.train()
    trainer.save_model(str(out))
    print(trainer.evaluate())


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", type=Path, default=Path("models/field-extraction-lora"))
    parser.add_argument("--model", default=BASE_MODEL)
    parser.add_argument("--epochs", type=int, default=EPOCHS)
    args = parser.parse_args()
    finetune(args.out, args.model, epochs=args.epochs)


if __name__ == "__main__":
    main()
