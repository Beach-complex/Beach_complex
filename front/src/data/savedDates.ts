// Saved dates from calendar bookmark feature
export interface SavedDate {
  id: string;
  reservationId?: string;
  beachId?: string;
  beachName: string;
  date: Date;
  hour: number;
  status: 'free' | 'normal' | 'busy';
  createdAt: Date;
}

// In a real app, this would be persisted to localStorage or backend
let savedDates: SavedDate[] = [];

const parseStoredDates = (raw: string): SavedDate[] => {
  const parsed = JSON.parse(raw);
  return parsed.map((d: any) => ({
    ...d,
    date: new Date(d.date),
    createdAt: new Date(d.createdAt),
  }));
};

export const addSavedDate = (date: SavedDate) => {
  const next = [...getSavedDates(), date];
  savedDates = next;
  // In real app: save to localStorage
  if (typeof window !== 'undefined') {
    localStorage.setItem('beachcheck_saved_dates', JSON.stringify(next));
  }
};

export const removeSavedDate = (id: string) => {
  const next = getSavedDates().filter(d => d.id !== id);
  savedDates = next;
  // In real app: save to localStorage
  if (typeof window !== 'undefined') {
    localStorage.setItem('beachcheck_saved_dates', JSON.stringify(next));
  }
};

export const getSavedDates = (): SavedDate[] => {
  // In real app: load from localStorage
  if (typeof window !== 'undefined') {
    const stored = localStorage.getItem('beachcheck_saved_dates');
    if (stored) {
      return parseStoredDates(stored);
    }
  }
  return savedDates;
};

export const setSavedDates = (next: SavedDate[]) => {
  savedDates = next;
  if (typeof window !== 'undefined') {
    localStorage.setItem('beachcheck_saved_dates', JSON.stringify(next));
  }
};

export const getSavedDatesForMonth = (year: number, month: number): SavedDate[] => {
  return getSavedDates().filter(saved => {
    const savedDate = new Date(saved.date);
    return savedDate.getFullYear() === year && savedDate.getMonth() === month;
  });
};
