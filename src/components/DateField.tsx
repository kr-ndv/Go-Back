import { useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';
import DateTimePicker, { DateTimePickerAndroid } from '@react-native-community/datetimepicker';
import { Ionicons } from '@expo/vector-icons';
import { useAppTheme } from '@/state/theme';

type Props = {
  value: string;
  onChange: (value: string) => void;
  placeholder: string;
  optional?: boolean;
  minimumDate?: Date;
};

const parseDate = (value: string) => {
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [year, month, day] = value.split('-').map(Number);
    const date = new Date(year, month - 1, day, 12, 0, 0);
    if (date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day) return date;
  }
  return new Date();
};
const toISODate = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
const displayDate = (value: string) => value ? parseDate(value).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : '';

export function DateField({ value, onChange, placeholder, optional = false, minimumDate }: Props) {
  const theme = useAppTheme();
  const [showPicker, setShowPicker] = useState(false);
  const openPicker = () => {
    const current = parseDate(value);
    if (Platform.OS === 'android') {
      DateTimePickerAndroid.open({
        value: current,
        mode: 'date',
        display: 'calendar',
        minimumDate,
        onChange: (event, selectedDate) => {
          if (event.type === 'set' && selectedDate) onChange(toISODate(selectedDate));
        },
      });
    } else {
      setShowPicker(true);
    }
  };
  return (
    <View>
      <View style={[styles.field, { borderColor: theme.border, backgroundColor: theme.surfaceAlt }]}>
        <Pressable onPress={openPicker} style={styles.pick} accessibilityRole="button" accessibilityLabel={placeholder}>
          <Ionicons name="calendar-outline" size={18} color={theme.primary} />
          <Text style={[styles.label, { color: value ? theme.text : theme.muted }]}>{value ? displayDate(value) : placeholder}</Text>
          <Ionicons name="chevron-down" size={16} color={theme.muted} />
        </Pressable>
        {optional && value ? <Pressable onPress={() => onChange('')} hitSlop={8} accessibilityLabel="Clear date" style={styles.clear}><Ionicons name="close-circle" size={18} color={theme.muted} /></Pressable> : null}
      </View>
      {showPicker && Platform.OS !== 'android' ? <DateTimePicker value={parseDate(value)} mode="date" display="default" minimumDate={minimumDate} onChange={(event, selectedDate) => { setShowPicker(false); if (event.type === 'set' && selectedDate) onChange(toISODate(selectedDate)); }} /> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  field: { borderWidth: 1, borderRadius: 12, minHeight: 44, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 11 },
  pick: { flex: 1, minHeight: 44, flexDirection: 'row', alignItems: 'center', gap: 9 },
  label: { flex: 1, fontSize: 12, fontWeight: '600' },
  clear: { paddingLeft: 8, paddingVertical: 8 },
});
