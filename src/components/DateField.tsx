import { useState } from 'react';
import { Modal, Pressable, StyleSheet, Text, View } from 'react-native';
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
const dateAtNoon = (date: Date) => new Date(date.getFullYear(), date.getMonth(), date.getDate(), 12, 0, 0);
const displayDate = (value: string) => value ? parseDate(value).toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' }) : '';

export function DateField({ value, onChange, placeholder, optional = false, minimumDate }: Props) {
  const theme = useAppTheme();
  const [visible, setVisible] = useState(false);
  const [month, setMonth] = useState(() => {
    const date = parseDate(value);
    return new Date(date.getFullYear(), date.getMonth(), 1);
  });
  const [pendingDate, setPendingDate] = useState(value || toISODate(new Date()));
  const openPicker = () => {
    const todayOrSelected = dateAtNoon(parseDate(value || (minimumDate ? toISODate(minimumDate) : toISODate(new Date()))));
    const min = minimumDate ? dateAtNoon(minimumDate) : null;
    const initial = min && todayOrSelected < min ? min : todayOrSelected;
    setPendingDate(value || toISODate(initial));
    setMonth(new Date(initial.getFullYear(), initial.getMonth(), 1));
    setVisible(true);
  };
  const firstWeekday = new Date(month.getFullYear(), month.getMonth(), 1).getDay();
  const daysInMonth = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate();
  const cells: (number | null)[] = [
    ...Array.from({ length: firstWeekday }, () => null),
    ...Array.from({ length: daysInMonth }, (_, index) => index + 1),
  ];
  const minDay = minimumDate ? dateAtNoon(minimumDate).getTime() : null;
  const previousMonth = new Date(month.getFullYear(), month.getMonth() - 1, 1);
  const previousDisabled = !!minimumDate && previousMonth < new Date(minimumDate.getFullYear(), minimumDate.getMonth(), 1);
  const close = () => setVisible(false);
  const confirm = () => { onChange(pendingDate); setVisible(false); };

  return (
    <>
      <View style={[styles.field, { borderColor: theme.border, backgroundColor: theme.surfaceAlt }]}>
        <Pressable onPress={openPicker} style={styles.pick} accessibilityRole="button" accessibilityLabel={placeholder}>
          <Ionicons name="calendar-outline" size={18} color={theme.primary} />
          <Text style={[styles.label, { color: value ? theme.text : theme.muted }]}>{value ? displayDate(value) : placeholder}</Text>
          <Ionicons name="chevron-down" size={16} color={theme.muted} />
        </Pressable>
        {optional && value ? <Pressable onPress={() => onChange('')} hitSlop={8} accessibilityLabel="Clear date" style={styles.clear}><Ionicons name="close-circle" size={18} color={theme.muted} /></Pressable> : null}
      </View>
      <Modal transparent visible={visible} animationType="fade" statusBarTranslucent onRequestClose={close}>
        <View style={styles.overlay}>
          <Pressable accessibilityLabel="Close date picker" style={StyleSheet.absoluteFill} onPress={close} />
          <View style={[styles.calendar, { backgroundColor: theme.surface, borderColor: theme.border }]}>
            <View style={styles.calendarTop}>
              <View style={[styles.calendarGlyph, { backgroundColor: theme.surfaceAlt }]}><Ionicons name="calendar" size={21} color={theme.primary} /></View>
              <View style={{ flex: 1, gap: 3 }}>
                <Text style={[styles.calendarEyebrow, { color: theme.muted }]}>CHOOSE A DATE</Text>
                <Text style={[styles.calendarTitle, { color: theme.text }]}>{pendingDate ? displayDate(pendingDate) : 'No date selected'}</Text>
              </View>
              <Pressable onPress={close} accessibilityLabel="Close calendar" hitSlop={8} style={styles.closeButton}><Ionicons name="close" size={21} color={theme.muted} /></Pressable>
            </View>
            <View style={[styles.monthBar, { backgroundColor: theme.surfaceAlt }]}>
              <Pressable disabled={previousDisabled} onPress={() => setMonth(previousMonth)} accessibilityLabel="Previous month" style={[styles.monthArrow, { opacity: previousDisabled ? 0.3 : 1 }]}><Ionicons name="chevron-back" size={19} color={theme.text} /></Pressable>
              <Text style={[styles.monthTitle, { color: theme.text }]}>{month.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })}</Text>
              <Pressable onPress={() => setMonth(new Date(month.getFullYear(), month.getMonth() + 1, 1))} accessibilityLabel="Next month" style={styles.monthArrow}><Ionicons name="chevron-forward" size={19} color={theme.text} /></Pressable>
            </View>
            <View style={styles.grid}>
              {['S', 'M', 'T', 'W', 'T', 'F', 'S'].map((day, index) => <Text key={day + index} style={[styles.weekday, { color: theme.muted }]}>{day}</Text>)}
              {cells.map((day, index) => {
                if (day === null) return <View key={'blank-' + index} style={styles.dayCell} />;
                const candidate = new Date(month.getFullYear(), month.getMonth(), day, 12, 0, 0);
                const iso = toISODate(candidate);
                const selected = iso === pendingDate;
                const disabled = minDay !== null && candidate.getTime() < minDay;
                const isToday = iso === toISODate(new Date());
                return (
                  <Pressable key={iso} disabled={disabled} accessibilityRole="button" accessibilityLabel={candidate.toLocaleDateString()} accessibilityState={{ selected, disabled }} onPress={() => setPendingDate(iso)} style={[styles.dayCell, styles.dayButton, { backgroundColor: selected ? theme.primary : isToday ? theme.surfaceAlt : 'transparent', borderColor: selected ? theme.primary : isToday ? theme.border : 'transparent', opacity: disabled ? 0.3 : 1 }]}>
                    <Text style={[styles.dayText, { color: selected ? theme.background : theme.text, fontWeight: selected || isToday ? '900' : '600' }]}>{day}</Text>
                  </Pressable>
                );
              })}
            </View>
            <View style={[styles.calendarFooter, { borderTopColor: theme.border }]}>
              <Pressable onPress={close} style={[styles.footerButton, { backgroundColor: theme.surfaceAlt, borderColor: theme.border }]}><Text style={[styles.footerText, { color: theme.text }]}>Cancel</Text></Pressable>
              <Pressable onPress={confirm} style={[styles.footerButton, { backgroundColor: theme.primary, borderColor: theme.primary }]}><Text style={[styles.footerText, { color: theme.background }]}>Use date</Text></Pressable>
            </View>
          </View>
        </View>
      </Modal>
    </>
  );
}

const styles = StyleSheet.create({
  field: { borderWidth: 1, borderRadius: 14, minHeight: 46, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 11 },
  pick: { flex: 1, minHeight: 46, flexDirection: 'row', alignItems: 'center', gap: 9 },
  label: { flex: 1, fontSize: 12, fontWeight: '600' },
  clear: { paddingLeft: 8, paddingVertical: 8 },
  overlay: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 20, backgroundColor: 'rgba(0,0,0,0.62)' },
  calendar: { width: '100%', maxWidth: 390, borderWidth: 1, borderRadius: 26, padding: 17, gap: 14, elevation: 24, shadowColor: '#000', shadowOpacity: 0.25, shadowRadius: 22, shadowOffset: { width: 0, height: 10 } },
  calendarTop: { flexDirection: 'row', alignItems: 'center', gap: 11 },
  calendarGlyph: { width: 43, height: 43, borderRadius: 14, alignItems: 'center', justifyContent: 'center' },
  calendarEyebrow: { fontSize: 9, letterSpacing: 1.4, fontWeight: '900' },
  calendarTitle: { fontSize: 14, fontWeight: '800' },
  closeButton: { width: 34, height: 34, alignItems: 'center', justifyContent: 'center' },
  monthBar: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderRadius: 15, paddingHorizontal: 7, paddingVertical: 4 },
  monthArrow: { width: 36, height: 36, alignItems: 'center', justifyContent: 'center' },
  monthTitle: { fontSize: 14, fontWeight: '900' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'flex-start' },
  weekday: { width: '14.2857%', textAlign: 'center', fontSize: 10, fontWeight: '800', paddingVertical: 8 },
  dayCell: { width: '14.2857%', aspectRatio: 1, alignItems: 'center', justifyContent: 'center' },
  dayButton: { borderWidth: 1, borderRadius: 13 },
  dayText: { fontSize: 12 },
  calendarFooter: { flexDirection: 'row', justifyContent: 'flex-end', gap: 8, borderTopWidth: 1, paddingTop: 13 },
  footerButton: { minHeight: 43, minWidth: 98, borderRadius: 14, borderWidth: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 15 },
  footerText: { fontSize: 12, fontWeight: '900' },
});
