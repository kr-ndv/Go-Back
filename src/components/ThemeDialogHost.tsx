import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useAppTheme } from '@/state/theme';
import { useDialogStore } from '@/state/dialog';

export function ThemeDialogHost() {
  const theme = useAppTheme();
  const visible = useDialogStore((state) => state.visible);
  const title = useDialogStore((state) => state.title);
  const message = useDialogStore((state) => state.message);
  const buttons = useDialogStore((state) => state.buttons);
  const close = useDialogStore((state) => state.close);

  const pressButton = (button: (typeof buttons)[number]) => {
    close();
    button.onPress?.();
  };

  return (
    <Modal transparent visible={visible} animationType="fade" statusBarTranslucent onRequestClose={close}>
      <View style={styles.overlay}>
        <Pressable accessibilityLabel="Close dialog" style={StyleSheet.absoluteFill} onPress={close} />
        <View style={[styles.card, { backgroundColor: theme.surfaceElevated, borderColor: theme.border, borderRadius: theme.expressive ? 32 : 24 }]}>
          <View style={[styles.iconBubble, { backgroundColor: theme.surfaceAlt }]}>
            <Ionicons name="leaf-outline" size={23} color={theme.primary} />
          </View>
          <Text accessibilityRole="header" style={[styles.title, { color: theme.text }]}>{title}</Text>
          {!!message && <Text style={[styles.message, { color: theme.muted }]}>{message}</Text>}
          <View style={styles.actions}>
            {buttons.map((button, index) => {
              const destructive = button.style === 'destructive';
              const cancel = button.style === 'cancel';
              return (
                <Pressable
                  key={button.text + index}
                  accessibilityRole="button"
                  onPress={() => pressButton(button)}
                  style={[
                    styles.action,
                    {
                      backgroundColor: cancel ? theme.surfaceAlt : destructive ? theme.danger : theme.primary,
                      borderColor: cancel ? theme.border : destructive ? theme.danger : theme.primary,
                      borderRadius: theme.expressive ? 18 : 14,
                    },
                  ]}
                >
                  <Text style={[styles.actionText, { color: cancel ? theme.text : theme.onPrimary }]}>{button.text}</Text>
                </Pressable>
              );
            })}
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 24, backgroundColor: 'rgba(0,0,0,0.58)' },
  card: { width: '100%', maxWidth: 420, borderWidth: 1, borderRadius: 28, padding: 24, gap: 12, elevation: 24, shadowColor: '#000000', shadowOpacity: 0.24, shadowRadius: 22, shadowOffset: { width: 0, height: 10 } },
  iconBubble: { width: 46, height: 46, borderRadius: 16, alignItems: 'center', justifyContent: 'center' },
  title: { fontSize: 20, fontWeight: '900', letterSpacing: -0.4 },
  message: { fontSize: 13, lineHeight: 20 },
  actions: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'flex-end', gap: 8, marginTop: 5 },
  action: { minHeight: 43, borderWidth: 1, borderRadius: 16, paddingHorizontal: 16, alignItems: 'center', justifyContent: 'center', minWidth: 86 },
  actionText: { fontSize: 12, fontWeight: '800' },
});
