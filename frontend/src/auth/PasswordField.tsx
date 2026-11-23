import VisibilityOffOutlinedIcon from '@mui/icons-material/VisibilityOffOutlined'
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined'
import { IconButton, InputAdornment, TextField, type TextFieldProps } from '@mui/material'
import { useState, type Ref } from 'react'

type Props = Omit<TextFieldProps, 'type'> & { ref?: Ref<HTMLDivElement> }

/** A password field with a button that shows what was typed, so a keyboard left in Greek is easy to spot. */
export function PasswordField(props: Props) {
  const [visible, setVisible] = useState(false)

  return (
    <TextField
      {...props}
      type={visible ? 'text' : 'password'}
      slotProps={{
        input: {
          endAdornment: (
            <InputAdornment position="end">
              <IconButton
                aria-label={visible ? 'Απόκρυψη κωδικού' : 'Εμφάνιση κωδικού'}
                onClick={() => setVisible(shown => !shown)}
                edge="end"
              >
                {visible ? <VisibilityOffOutlinedIcon /> : <VisibilityOutlinedIcon />}
              </IconButton>
            </InputAdornment>
          ),
        },
      }}
    />
  )
}
