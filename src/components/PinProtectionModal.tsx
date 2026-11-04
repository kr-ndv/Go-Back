import { useState, useEffect } from 'react';
import { Modal, StyleSheet, Text, View } from 'react-native';
import { MotionPressable as Pressable } from '@/components/MotionPressable';
import { Ionicons } from '@expo/vector-icons';
import { useAppTheme } from '@/state/theme';
import { getSecurityPin, setSecurityPin, matchesPin } from '@/lib/securityPin';

type PinMode = 'verify' | 'set' | 'remove';

type Props = {
  visible: boolean;
  mode?: PinMode;
  title?: string;
  onSuccess: () => void;
  onCancel: () => void;
};

export function PinProtectionModal({
  visible,
  mode = 'verify',
  title,
  onSuccess,
  onCancel,
}: Props) {
  const theme = useAppTheme();
  const [digits, setDigits] = useState('');
  const [firstPin, setFirstPin] = useState('');
  const [step, setStep] = useState<'enter' | 'confirm'>('enter');
  const [error, setError] = useState('');
  const [storedPin, setStoredPin] = useState<string | null>(null);

  useEffect(() => {
    if (visible) {
      setDigits('');
      setFirstPin('');
      setStep('enter');
      setError('');
      void getSecurityPin().then(pin => setStoredPin(pin));
    }
  }, [visible]);

  const handleDigit = (digit: string) => {
    if (digits.length >= 4) return;
    const next = digits + digit;
    setDigits(next);
    setError('');

    if (next.length === 4) {
      setTimeout(() => processFourDigits(next), 120);
    }
  };

  const handleBackspace = () => {
    if (digits.length > 0) {
      setDigits(digits.slice(0, -1));
      setError('');
    }
  };

  const processFourDigits = async (entered: string) => {
    if (mode === 'verify') {
      if (matchesPin(storedPin, entered)) {
        onSuccess();
      } else {
        setError('Incorrect PIN. Try again.');
        setDigits('');
      }
    } else if (mode === 'remove') {
      if (matchesPin(storedPin, entered)) {
        await setSecurityPin(null);
        onSuccess();
      } else {
        setError('Incorrect PIN. Cannot remove lock.');
        setDigits('');
      }
    } else if (mode === 'set') {
      if (step === 'enter') {
        setFirstPin(entered);
        setStep('confirm');
        setDigits('');
      } else {
        if (entered === firstPin) {
          await setSecurityPin(entered);
          onSuccess();
        } else {
          setError('PINs do not match. Try again.');
          setFirstPin('');
          setStep('enter');
          setDigits('');
        }
      }
    }
  };

  const displayTitle = title ?? (
    mode === 'set'
      ? (step === 'enter' ? 'Create a 4-digit PIN' : 'Confirm your 4-digit PIN')
      : mode === 'remove'
      ? 'Enter current PIN to remove'
      : 'Security PIN required'
  );

  const displaySubtitle = mode === 'set'
    ? (step === 'enter' ? 'Set a PIN to protect blocking and app limits from impulse changes.' : 'Re-enter your PIN to verify.')
    : mode === 'remove'
    ? 'Enter your current PIN to turn off PIN protection.'
    : 'Enter your 4-digit PIN to access protected focus settings.';

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onCancel}>
      <View style={styles.backdrop}>
        <View style={[styles.card, { backgroundColor: theme.surfaceElevated, borderColor: theme.border }]}>
          <View style={[styles.badge, { backgroundColor: theme.primaryContainer }]}>
            <Ionicons name="keypad-outline" size={26} color={theme.primary} />
          </View>
          <Text style={[styles.title, { color: theme.text }]}>{displayTitle}</Text>
          <Text style={[styles.subtitle, { color: theme.muted }]}>{displaySubtitle}</Text>

          {/* 4 Dots */}
          <View style={styles.dotsRow}>
            {[0, 1, 2, 3].map(index => {
              const filled = digits.length > index;
              return (
                <View
                  key={index}
                  style={[
                    styles.dot,
                    {
                      borderColor: theme.primary,
                      backgroundColor: filled ? theme.primary : 'transparent',
                    },
                  ]}
                />
              );
            })}
          </View>

          {!!error && <Text style={[styles.error, { color: theme.danger }]}>{error}</Text>}

          {/* Keypad */}
          <View style={styles.keypad}>
            {[['1', '2', '3'], ['4', '5', '6'], ['7', '8', '9']].map((row, rIdx) => (
              <View key={rIdx} style={styles.keyRow}>
                {row.map(num => (
                  <Pressable
                    key={num}
                    accessibilityRole="button"
                    accessibilityLabel={`Digit ${num}`}
                    onPress={() => handleDigit(num)}
                    style={[styles.key, { backgroundColor: theme.surfaceAlt }]}
                  >
                    <Text style={[styles.keyText, { color: theme.text }]}>{num}</Text>
                  </Pressable>
                ))}
              </View>
            ))}
            <View style={styles.keyRow}>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Cancel"
                onPress={onCancel}
                style={[styles.key, { backgroundColor: 'transparent' }]}
              >
                <Text style={[styles.actionText, { color: theme.muted }]}>Cancel</Text>
              </Pressable>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Digit 0"
                onPress={() => handleDigit('0')}
                style={[styles.key, { backgroundColor: theme.surfaceAlt }]}
              >
                <Text style={[styles.keyText, { color: theme.text }]}>0</Text>
              </Pressable>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="Backspace"
                onPress={handleBackspace}
                style={[styles.key, { backgroundColor: 'transparent' }]}
              >
                <Ionicons name="backspace-outline" size={24} color={theme.text} />
              </Pressable>
            </View>
          </View>
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.65)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  card: {
    width: '100%',
    maxWidth: 340,
    borderRadius: 26,
    borderWidth: 1,
    padding: 22,
    alignItems: 'center',
    gap: 12,
  },
  badge: {
    width: 52,
    height: 52,
    borderRadius: 26,
    alignItems: 'center',
    justifyContent: 'center',
  },
  title: {
    fontSize: 17,
    fontWeight: '900',
    textAlign: 'center',
  },
  subtitle: {
    fontSize: 12,
    lineHeight: 17,
    textAlign: 'center',
  },
  dotsRow: {
    flexDirection: 'row',
    gap: 16,
    marginVertical: 10,
  },
  dot: {
    width: 14,
    height: 14,
    borderRadius: 7,
    borderWidth: 2,
  },
  error: {
    fontSize: 12,
    fontWeight: '700',
    textAlign: 'center',
  },
  keypad: {
    width: '100%',
    gap: 10,
    marginTop: 4,
  },
  keyRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
  },
  key: {
    flex: 1,
    height: 54,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  keyText: {
    fontSize: 20,
    fontWeight: '800',
  },
  actionText: {
    fontSize: 13,
    fontWeight: '700',
  },
});
