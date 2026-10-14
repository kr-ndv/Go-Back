import { create } from 'zustand';

export type DialogButton = {
  text: string;
  style?: 'default' | 'cancel' | 'destructive';
  onPress?: () => void;
};

type DialogState = {
  visible: boolean;
  title: string;
  message: string;
  buttons: DialogButton[];
  open: (title: string, message: string, buttons: DialogButton[]) => void;
  close: () => void;
};

export const useDialogStore = create<DialogState>((set) => ({
  visible: false,
  title: '',
  message: '',
  buttons: [],
  open: (title, message, buttons) => set({ visible: true, title, message, buttons }),
  close: () => set({ visible: false }),
}));

export function showThemedDialog(
  title: string,
  message = '',
  buttons: DialogButton[] = [{ text: 'OK', style: 'default' }],
) {
  useDialogStore.getState().open(title, message, buttons);
}
