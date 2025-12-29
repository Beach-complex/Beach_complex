import { useState, useMemo, useEffect } from 'react';
import { Slider } from './ui/slider';
import { CalendarPlus } from 'lucide-react';
import { toast } from 'sonner@2.0.3';
import { addSavedDate } from '../data/savedDates';

interface DayData {
  date: number;
  month: number;
  year: number;
  dayOfWeek: string;
  status: 'free' | 'normal' | 'busy';
}

interface HourlyData {
  hour: number;
  status: 'free' | 'normal' | 'busy';
  percentage: number;
}

interface MonthlyHeatmapProps {
  month: number;
  year: number;
  beachName: string;
  hourlyData: HourlyData[];
  onDateSelect?: (date: DayData) => void;
  externalDate?: Date | undefined;
  isAuthenticated?: boolean;
  onAuthRequired?: () => void;
}

export function MonthlyHeatmap({
  month,
  year,
  beachName,
  hourlyData,
  onDateSelect,
  externalDate,
  isAuthenticated,
  onAuthRequired,
}: MonthlyHeatmapProps) {
  const [selectedDate, setSelectedDate] = useState<DayData | null>(null);
  const [selectedHour, setSelectedHour] = useState<number>(new Date().getHours());
  const [isInitialMount, setIsInitialMount] = useState(true);

  useEffect(() => {
    if (isInitialMount) {
      setIsInitialMount(false);
      return;
    }
    
    if (externalDate) {
      const date = externalDate.getDate();
      const monthNum = externalDate.getMonth() + 1;
      const yearNum = externalDate.getFullYear();
      const dayOfWeek = new Date(yearNum, monthNum - 1, date).getDay();
      const dayNames = ['일', '월', '화', '수', '목', '금', '토'];
      const status = getStatusForDateTime(date, selectedHour);
      
      setSelectedDate({
        date,
        month: monthNum,
        year: yearNum,
        dayOfWeek: dayNames[dayOfWeek],
        status,
      });
    }
  }, [externalDate, selectedHour]);

  const statusColors = {
    busy: '#FF0000',
    normal: '#FFEA00',
    free: '#51FF00',
  };

  const statusLabels = {
    busy: '혼잡',
    normal: '보통',
    free: '여유',
  };

  const getStatusForDateTime = (dateNum: number, hour: number): 'free' | 'normal' | 'busy' => {
    const seed = (dateNum * 100 + hour) % 7;
    const isWeekend = new Date(year, month - 1, dateNum).getDay() % 6 === 0;
    
    if (isWeekend) {
      if (hour >= 11 && hour <= 17) {
        return seed < 5 ? 'busy' : 'normal';
      } else if ((hour >= 9 && hour < 11) || (hour > 17 && hour <= 19)) {
        return seed < 3 ? 'normal' : seed < 6 ? 'busy' : 'free';
      } else {
        return seed < 5 ? 'free' : 'normal';
      }
    }
    
    if (hour >= 12 && hour <= 16) {
      return seed < 4 ? 'busy' : 'normal';
    } else if ((hour >= 10 && hour < 12) || (hour > 16 && hour <= 18)) {
      return seed < 3 ? 'normal' : seed < 6 ? 'free' : 'busy';
    } else {
      return seed < 6 ? 'free' : 'normal';
    }
  };

  const monthData = useMemo(() => {
    const daysInMonth = new Date(year, month, 0).getDate();
    const firstDay = new Date(year, month - 1, 1).getDay();
    const days: DayData[] = [];
    const dayNames = ['일', '월', '화', '수', '목', '금', '토'];

    for (let i = 0; i < firstDay; i++) {
      days.push({ date: 0, month: 0, year: 0, dayOfWeek: '', status: 'free' });
    }

    for (let i = 1; i <= daysInMonth; i++) {
      const dayOfWeek = new Date(year, month - 1, i).getDay();
      const status = getStatusForDateTime(i, selectedHour);
      days.push({ date: i, month, year, dayOfWeek: dayNames[dayOfWeek], status });
    }

    return days;
  }, [year, month, selectedHour]);

  const weeks = [];
  for (let i = 0; i < monthData.length; i += 7) {
    weeks.push(monthData.slice(i, i + 7));
  }

  const handleDateClick = (day: DayData) => {
    if (day.date === 0) return;
    const updatedDay = { ...day, status: getStatusForDateTime(day.date, selectedHour) };
    setSelectedDate(updatedDay);
    onDateSelect?.(updatedDay);
  };

  const handleHourChange = (newHour: number) => {
    setSelectedHour(newHour);
    if (selectedDate) {
      const updatedStatus = getStatusForDateTime(selectedDate.date, newHour);
      setSelectedDate({ ...selectedDate, status: updatedStatus });
    }
  };

  const handleAddToCalendar = () => {
    if (isAuthenticated === false) {
      onAuthRequired?.();
      return;
    }
    if (!selectedDate) {
      toast.error('날짜를 먼저 선택해주세요');
      return;
    }

    addSavedDate({
      id: `${beachName}-${selectedDate.year}-${selectedDate.month}-${selectedDate.date}-${selectedHour}-${Date.now()}`,
      beachName,
      date: new Date(selectedDate.year, selectedDate.month - 1, selectedDate.date),
      hour: selectedHour,
      status: selectedDate.status,
      createdAt: new Date(),
    });

    const dateStr = `${selectedDate.year}년 ${selectedDate.month}월 ${selectedDate.date}일 (${selectedDate.dayOfWeek})`;
    const timeStr = `${selectedHour}:00`;
    
    toast.success('캘린더에 추가되었습니다', {
      description: `${beachName} - ${dateStr} ${timeStr}`,
    });
  };

  return (
    <div className="bg-card dark:bg-gray-800 rounded-xl p-5 shadow-sm border border-border">
      <div className="grid grid-cols-7 gap-2 mb-3">
        {['일', '월', '화', '수', '목', '금', '토'].map((day, idx) => (
          <div key={idx} className="text-center">
            <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[11px] text-muted-foreground">
              {day}
            </span>
          </div>
        ))}
      </div>

      <div className="space-y-2 mb-4">
        {weeks.map((week, weekIdx) => (
          <div key={weekIdx} className="grid grid-cols-7 gap-2">
            {week.map((day, dayIdx) => (
              <div
                key={`${weekIdx}-${dayIdx}`}
                className="aspect-square flex items-center justify-center"
              >
                {day.date > 0 && (
                  <button
                    onClick={() => handleDateClick(day)}
                    className={`w-[38px] h-[38px] rounded-full transition-all hover:scale-110 flex items-center justify-center relative ${
                      selectedDate?.date === day.date && selectedDate?.month === day.month
                        ? 'ring-2 ring-blue-500 ring-offset-2 dark:ring-offset-gray-800'
                        : ''
                    }`}
                    style={{ backgroundColor: statusColors[day.status] }}
                    title={`${day.date}일 - ${statusLabels[day.status]}`}
                  >
                    <span className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[12px] text-white drop-shadow-md">
                      {day.date}
                    </span>
                  </button>
                )}
              </div>
            ))}
          </div>
        ))}
      </div>

      <div className="space-y-4 mb-4">
        <div className="bg-gradient-to-r from-blue-50 to-blue-100 dark:from-blue-900/30 dark:to-blue-800/30 rounded-lg p-4 border-l-4 border-blue-500">
          <div className="flex items-center justify-between mb-3">
            <div>
              <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-muted-foreground mb-1">
                선택한 날짜
              </p>
              {selectedDate ? (
                <p className="font-['Noto_Sans_KR:Bold',_sans-serif] text-foreground">
                  {selectedDate.month}월 {selectedDate.date}일 ({selectedDate.dayOfWeek})
                </p>
              ) : (
                <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-muted-foreground">
                  날짜를 선택해주세요
                </p>
              )}
            </div>
            <div className="text-right">
              <p className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-muted-foreground mb-1">
                예상 혼잡도
              </p>
              {selectedDate ? (
                <p 
                  className="font-['Noto_Sans_KR:Bold',_sans-serif]"
                  style={{
                    color:
                      selectedDate.status === 'busy'
                        ? '#FF0000'
                        : selectedDate.status === 'normal'
                        ? '#FFA500'
                        : '#51FF00',
                  }}
                >
                  {statusLabels[selectedDate.status]}
                </p>
              ) : (
                <p className="font-['Noto_Sans_KR:Regular',_sans-serif] text-muted-foreground">
                  -
                </p>
              )}
            </div>
          </div>

          <div className="flex items-center justify-center gap-6 pt-3 border-t border-border">
            <div className="flex items-center gap-2">
              <div className="w-6 h-6 rounded-full" style={{ backgroundColor: statusColors.busy }} />
              <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-foreground">
                혼잡
              </span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-6 h-6 rounded-full" style={{ backgroundColor: statusColors.normal }} />
              <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-foreground">
                보통
              </span>
            </div>
            <div className="flex items-center gap-2">
              <div className="w-6 h-6 rounded-full" style={{ backgroundColor: statusColors.free }} />
              <span className="font-['Noto_Sans_KR:Medium',_sans-serif] text-[12px] text-foreground">
                여유
              </span>
            </div>
          </div>
        </div>

        <div className="bg-muted dark:bg-gray-900/50 rounded-lg p-4 border border-border">
          <div className="flex items-center justify-between mb-3">
            <label className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[14px] text-foreground">
              방문 시간 선택
            </label>
            <span className="font-['Noto_Sans_KR:Bold',_sans-serif] text-[16px] text-blue-600 dark:text-blue-400">
              {selectedHour}:00
            </span>
          </div>
          <Slider
            value={[selectedHour]}
            onValueChange={(value) => handleHourChange(value[0])}
            min={0}
            max={23}
            step={1}
            className="mb-2"
          />
          <div className="flex justify-between">
            <span className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[11px] text-muted-foreground">
              0시
            </span>
            <span className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[11px] text-muted-foreground">
              12시
            </span>
            <span className="font-['Noto_Sans_KR:Regular',_sans-serif] text-[11px] text-muted-foreground">
              23시
            </span>
          </div>
        </div>

        <button
          onClick={handleAddToCalendar}
          className="w-full bg-blue-600 hover:bg-blue-700 dark:bg-blue-600 dark:hover:bg-blue-700 text-white rounded-lg py-3 px-4 flex items-center justify-center gap-2 transition-colors shadow-md hover:shadow-lg"
        >
          <CalendarPlus className="w-5 h-5" />
          <span className="font-['Noto_Sans_KR:Bold',_sans-serif]">
            내 캘린더에 추가
          </span>
        </button>
      </div>
    </div>
  );
}
