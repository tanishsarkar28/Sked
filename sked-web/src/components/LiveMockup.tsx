import React, { useState, useEffect } from 'react';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { 
  RefreshCw, 
  LayoutGrid, 
  LogOut, 
  CalendarClock, 
  GraduationCap, 
  QrCode, 
  ShieldCheck, 
  Clock, 
  MapPin, 
  AlertTriangle, 
  Check, 
  ExternalLink,
  Sparkles,
  Lock,
  ChevronRight
} from 'lucide-react';

interface MockClass {
  code: string;
  name: string;
  time: string;
  room: string;
  faculty: string;
  type: 'LEC' | 'PRAC' | 'TUT';
  status: 'OVER' | 'UPCOMING' | 'ON GOING' | 'PENDING';
}

type ActiveView = 'timetable' | 'exams' | 'leave' | 'slip';

export const LiveMockup: React.FC = () => {
  const [activeView, setActiveView] = useState<ActiveView>('timetable');
  const [selectedDay, setSelectedDay] = useState<string>('THU');
  const [selectedLeaveType, setSelectedLeaveType] = useState<'day' | 'night'>('night');
  const [selectedPlace, setSelectedPlace] = useState<string>('Phagwara');
  const [selectedReason, setSelectedReason] = useState<string>('Personal work');
  const [showOtpDialog, setShowOtpDialog] = useState<boolean>(false);
  const [isSyncing, setIsSyncing] = useState<boolean>(false);
  const [currentTime, setCurrentTime] = useState<string>('15:42:09 PM');

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      setCurrentTime(now.toLocaleTimeString('en-US', { hour12: true, hour: '2-digit', minute: '2-digit', second: '2-digit' }));
    };
    updateTime();
    const interval = setInterval(updateTime, 1000);
    return () => clearInterval(interval);
  }, []);

  const handleSyncClick = () => {
    setIsSyncing(true);
    setTimeout(() => {
      setIsSyncing(false);
    }, 1200);
  };

  const days = [
    { name: 'MON', count: 4 },
    { name: 'TUE', count: 3 },
    { name: 'WED', count: 5 },
    { name: 'THU', count: 5 },
    { name: 'FRI', count: 6 },
    { name: 'SAT', count: 2 },
    { name: 'SUN', count: 0 },
  ];

  const scheduleData: Record<string, MockClass[]> = {
    MON: [
      { code: 'INT257', name: 'Software Project Management', time: '10:20-11:10', room: '33-612', faculty: 'Dr. Amit Sharma • Sec K24RL', type: 'LEC', status: 'OVER' },
      { code: 'INT257', name: 'Software Project Management', time: '11:10-12:00', room: '33-612', faculty: 'Dr. Amit Sharma • Sec K24RL', type: 'LEC', status: 'OVER' },
      { code: 'CSE408', name: 'Design & Analysis of Algorithms', time: '13:40-14:30', room: '33-608', faculty: 'Prof. Rajesh Verma • Sec K24RL', type: 'LEC', status: 'OVER' },
      { code: 'CSE408', name: 'Design & Analysis of Algorithms', time: '14:30-15:20', room: '33-608', faculty: 'Prof. Rajesh Verma • Sec K24RL', type: 'LEC', status: 'OVER' },
    ],
    TUE: [
      { code: 'MKT311', name: 'Digital Marketing', time: '09:30-10:20', room: '37-905', faculty: 'Dr. Neha Kapoor • Sec K24RL', type: 'PRAC', status: 'OVER' },
      { code: 'INT252', name: 'Web App Dev with ReactJS', time: '12:50-13:40', room: '33-507Y', faculty: 'Dr. Amit Sharma • Sec K24RL', type: 'PRAC', status: 'ON GOING' },
      { code: 'CSE408', name: 'Design & Analysis of Algorithms', time: '15:30-16:20', room: '34-101', faculty: 'Prof. Rajesh Verma • Sec K24RL', type: 'LEC', status: 'UPCOMING' },
    ],
    WED: [
      { code: 'INT252', name: 'Web App Dev with ReactJS', time: '10:20-11:10', room: '33-507Y', faculty: 'Dr. Amit Sharma • Sec K24RL', type: 'PRAC', status: 'OVER' },
      { code: 'INT252', name: 'Web App Dev with ReactJS', time: '11:10-12:00', room: '33-507Y', faculty: 'Dr. Amit Sharma • Sec K24RL', type: 'PRAC', status: 'OVER' },
      { code: 'PEA306', name: 'Analytical Skills-II', time: '13:40-14:30', room: '34-101', faculty: 'Prof. Priya Sen • Sec K24RL', type: 'LEC', status: 'ON GOING' },
      { code: 'MKT311', name: 'Digital Marketing', time: '14:40-15:30', room: '37-905', faculty: 'Dr. Neha Kapoor • Sec K24RL', type: 'PRAC', status: 'UPCOMING' },
      { code: 'CSE408', name: 'Design & Analysis of Algorithms', time: '16:10-17:00', room: '34-101', faculty: 'Prof. Rajesh Verma • Sec K24RL', type: 'LEC', status: 'PENDING' },
    ],
    THU: [
      { code: 'INT252', name: 'Web App Dev with ReactJS', time: '10:20-11:10', room: '33-507Y', faculty: 'Dr. Amit Sharma • Sec K24RL', type: 'PRAC', status: 'OVER' },
      { code: 'INT252', name: 'Web App Dev with ReactJS', time: '11:10-12:00', room: '33-507Y', faculty: 'Dr. Amit Sharma • Sec K24RL', type: 'PRAC', status: 'OVER' },
      { code: 'MKT311', name: 'Digital Marketing', time: '12:50-13:40', room: '37-905', faculty: 'Dr. Neha Kapoor • Sec K24RL', type: 'PRAC', status: 'ON GOING' },
      { code: 'CSE408', name: 'Design & Analysis of Algorithms', time: '13:40-14:30', room: '34-101', faculty: 'Prof. Rajesh Verma • Sec K24RL', type: 'LEC', status: 'UPCOMING' },
      { code: 'PEA306', name: 'Analytical Skills-II', time: '16:10-17:00', room: '34-101', faculty: 'Prof. Priya Sen • Sec K24RL', type: 'LEC', status: 'PENDING' },
    ],
    FRI: [
      { code: 'CSE408', name: 'Design & Analysis of Algorithms', time: '09:30-10:20', room: '34-101', faculty: 'Prof. Rajesh Verma • Sec K24RL', type: 'LEC', status: 'OVER' },
      { code: 'INT252', name: 'Web App Dev with ReactJS', time: '10:20-11:10', room: '33-507Y', faculty: 'Dr. Amit Sharma • Sec K24RL', type: 'PRAC', status: 'OVER' },
      { code: 'INT252', name: 'Web App Dev with ReactJS', time: '11:10-12:00', room: '33-507Y', faculty: 'Dr. Amit Sharma • Sec K24RL', type: 'PRAC', status: 'OVER' },
      { code: 'MKT311', name: 'Digital Marketing', time: '12:50-13:40', room: '37-905', faculty: 'Dr. Neha Kapoor • Sec K24RL', type: 'PRAC', status: 'ON GOING' },
      { code: 'PEA306', name: 'Analytical Skills-II', time: '14:40-15:30', room: '34-101', faculty: 'Prof. Priya Sen • Sec K24RL', type: 'LEC', status: 'UPCOMING' },
      { code: 'INT257', name: 'Software Project Management', time: '16:10-17:00', room: '33-612', faculty: 'Dr. Amit Sharma • Sec K24RL', type: 'LEC', status: 'PENDING' },
    ],
    SAT: [
      { code: 'PEA306', name: 'Analytical Skills-II', time: '09:30-10:20', room: '34-101', faculty: 'Prof. Priya Sen • Sec K24RL', type: 'LEC', status: 'OVER' },
      { code: 'MKT311', name: 'Digital Marketing', time: '11:10-12:00', room: '37-905', faculty: 'Dr. Neha Kapoor • Sec K24RL', type: 'PRAC', status: 'UPCOMING' },
    ],
    SUN: [],
  };

  const examData = [
    {
      code: 'CSE408',
      name: 'Design & Analysis of Algorithms',
      type: 'MTE Theory',
      date: '24-Nov-2026',
      time: '09:00 AM - 12:00 PM',
      room: '34-204',
      seat: '18',
      daysLeft: 'in 8 days'
    },
    {
      code: 'INT252',
      name: 'Web Application Development',
      type: 'Practical / Lab',
      date: '26-Nov-2026',
      time: '01:30 PM - 04:30 PM',
      room: '33-507Y',
      seat: '05',
      daysLeft: 'in 10 days'
    },
    {
      code: 'PEA306',
      name: 'Analytical Skills-II',
      type: 'MTE Theory',
      date: '28-Nov-2026',
      time: '09:00 AM - 12:00 PM',
      room: '34-101',
      seat: '42',
      daysLeft: 'in 12 days'
    }
  ];

  const getStatusStyles = (status: MockClass['status']) => {
    switch (status) {
      case 'ON GOING':
        return {
          pill: 'text-emerald-400 bg-emerald-500/15 border-emerald-500',
          bar: 'border-l-emerald-500',
          border: 'border-emerald-500/40',
        };
      case 'UPCOMING':
        return {
          pill: 'text-[#FF8533] bg-[#FF8533]/15 border-[#FF8533]',
          bar: 'border-l-[#FF8533]',
          border: 'border-[#252525]',
        };
      case 'PENDING':
        return {
          pill: 'text-[#818CF8] bg-[#818CF8]/15 border-[#818CF8]/60',
          bar: 'border-l-[#818CF8]/70',
          border: 'border-[#252525]',
        };
      case 'OVER':
      default:
        return {
          pill: 'text-[#71717A] bg-[#27272A]/40 border-[#3F3F46]',
          bar: 'border-l-[#383838]',
          border: 'border-[#252525]',
        };
    }
  };

  const currentClasses = scheduleData[selectedDay] || [];

  return (
    <section id="preview" className="py-24 px-6 max-w-7xl mx-auto border-t border-[#252525]">
      <div className="text-center mb-16 space-y-4">
        <Badge variant="secondary" className="bg-[#FF6B1A]/10 text-[#FF6B1A] border-[#FF6B1A]/30 font-mono text-xs">
          INTERACTIVE APP & DASHBOARD PREVIEW
        </Badge>
        <h2 className="text-4xl sm:text-5xl font-bold font-['Barlow_Condensed'] text-[#E8E6E3] tracking-wide">
          2×2 QUICK ACCESS & DEPARTURES BOARD<span className="text-[#FF6B1A]">.</span>
        </h2>
        <p className="text-[#7A7774] text-lg max-w-2xl mx-auto">
          Tap any tile in the 2×2 launcher grid to test real app views: Timetable, Exam Radar, Hostel Leave, and the live Digital Gate Pass Slip with turnstile barcode.
        </p>

        {/* View Switcher Chips */}
        <div className="flex flex-wrap items-center justify-center gap-2 pt-2">
          <button
            onClick={() => setActiveView('timetable')}
            className={`px-3.5 py-1.5 rounded-xl text-xs font-mono transition-all ${
              activeView === 'timetable'
                ? 'bg-[#FF6B1A] text-[#0A0A0A] font-bold shadow'
                : 'bg-[#141414] text-[#7A7774] border border-[#252525] hover:text-[#E8E6E3]'
            }`}
          >
            📅 Timetable View
          </button>
          <button
            onClick={() => setActiveView('exams')}
            className={`px-3.5 py-1.5 rounded-xl text-xs font-mono transition-all ${
              activeView === 'exams'
                ? 'bg-[#FF6B1A] text-[#0A0A0A] font-bold shadow'
                : 'bg-[#141414] text-[#7A7774] border border-[#252525] hover:text-[#E8E6E3]'
            }`}
          >
            🎓 Exam Radar
          </button>
          <button
            onClick={() => setActiveView('leave')}
            className={`px-3.5 py-1.5 rounded-xl text-xs font-mono transition-all ${
              activeView === 'leave'
                ? 'bg-[#FF6B1A] text-[#0A0A0A] font-bold shadow'
                : 'bg-[#141414] text-[#7A7774] border border-[#252525] hover:text-[#E8E6E3]'
            }`}
          >
            📝 Apply Leave
          </button>
          <button
            onClick={() => setActiveView('slip')}
            className={`px-3.5 py-1.5 rounded-xl text-xs font-mono transition-all ${
              activeView === 'slip'
                ? 'bg-[#FF6B1A] text-[#0A0A0A] font-bold shadow'
                : 'bg-[#141414] text-[#7A7774] border border-[#252525] hover:text-[#E8E6E3]'
            }`}
          >
            🎫 Gate Pass Slip
          </button>
        </div>
      </div>

      <div className="flex justify-center">
        {/* Phone Frame */}
        <div className="w-full max-w-[400px] bg-[#0A0A0A] rounded-[44px] border-[6px] border-[#252525] p-4 shadow-2xl relative overflow-hidden">
          {/* Dynamic Island / Speaker */}
          <div className="w-28 h-4 bg-[#141414] rounded-full mx-auto mb-3" />

          {/* Phone Header */}
          <div className="flex items-center justify-between px-2 mb-3">
            <div className="flex items-center gap-1.5">
              <span className="font-['Barlow_Condensed'] font-bold text-2xl text-[#FF6B1A] tracking-wider">
                SKED<span className="text-[#E8E6E3]">.</span>
              </span>
            </div>
            <div className="flex items-center gap-3 text-[#7A7774]">
              <button 
                onClick={handleSyncClick} 
                className="hover:text-[#FF6B1A] transition-colors"
                title="Parallel sync (1.5s)"
              >
                <RefreshCw className={`w-4 h-4 ${isSyncing ? 'animate-spin text-[#FF6B1A]' : ''}`} />
              </button>
              <button 
                onClick={() => setActiveView(activeView === 'timetable' ? 'exams' : 'timetable')}
                className="hover:text-[#E8E6E3] transition-colors"
                title="Toggle Dashboard View"
              >
                <LayoutGrid className="w-4 h-4" />
              </button>
              <LogOut className="w-4 h-4 text-red-500/80 hover:text-red-400 cursor-pointer" />
            </div>
          </div>

          {/* User Registration Row */}
          <div className="flex items-center justify-between px-2 mb-3 py-1.5 bg-[#121212] rounded-xl border border-[#202020]">
            <div>
              <span className="text-[10px] font-mono font-bold text-[#7A7774] block">
                REG: 1240XXXX • SEC K24RL
              </span>
              <span className="text-xs font-bold text-[#E8E6E3] font-['Barlow_Condensed'] tracking-wide">
                {activeView === 'timetable' && (selectedDay === 'SUN' ? 'Sunday • Relax' : `${selectedDay} Schedule`)}
                {activeView === 'exams' && 'Datesheet & Seating Radar'}
                {activeView === 'leave' && 'Hostel Leave Application'}
                {activeView === 'slip' && 'Active Hostel Gate Pass Slip'}
              </span>
            </div>
            <span className="text-[9px] font-mono text-[#FF6B1A] bg-[#FF6B1A]/10 border border-[#FF6B1A]/30 px-2 py-0.5 rounded flex items-center gap-1">
              <Sparkles className="w-2.5 h-2.5" />
              SYNC: 1.5s
            </span>
          </div>

          {/* 2×2 Quick Access Launcher Grid */}
          <div className="grid grid-cols-2 gap-2 mb-4">
            {/* Tile 1: Timetable */}
            <button
              onClick={() => setActiveView('timetable')}
              className={`p-2.5 rounded-xl border text-left transition-all ${
                activeView === 'timetable'
                  ? 'bg-[#1C1510] border-[#FF6B1A] shadow-md shadow-[#FF6B1A]/10'
                  : 'bg-[#141414] border-[#252525] hover:border-[#383838]'
              }`}
            >
              <div className="flex items-center justify-between mb-1">
                <CalendarClock className={`w-4 h-4 ${activeView === 'timetable' ? 'text-[#FF6B1A]' : 'text-[#7A7774]'}`} />
                <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-[#0A0A0A] text-[#E8E6E3] border border-[#252525]">
                  5 TODAY
                </span>
              </div>
              <div className="font-['Barlow_Condensed'] font-bold text-sm text-[#E8E6E3] tracking-wide leading-none mb-0.5">
                TIMETABLE
              </div>
              <div className="text-[10px] font-mono text-[#7A7774]">
                1 On Going
              </div>
            </button>

            {/* Tile 2: Exams */}
            <button
              onClick={() => setActiveView('exams')}
              className={`p-2.5 rounded-xl border text-left transition-all ${
                activeView === 'exams'
                  ? 'bg-[#1C1510] border-[#FF6B1A] shadow-md shadow-[#FF6B1A]/10'
                  : 'bg-[#141414] border-[#252525] hover:border-[#383838]'
              }`}
            >
              <div className="flex items-center justify-between mb-1">
                <GraduationCap className={`w-4 h-4 ${activeView === 'exams' ? 'text-[#FF6B1A]' : 'text-[#7A7774]'}`} />
                <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-[#0A0A0A] text-[#818CF8] border border-[#818CF8]/30">
                  3 EXAMS
                </span>
              </div>
              <div className="font-['Barlow_Condensed'] font-bold text-sm text-[#E8E6E3] tracking-wide leading-none mb-0.5">
                EXAM RADAR
              </div>
              <div className="text-[10px] font-mono text-[#7A7774]">
                Next in 8d
              </div>
            </button>

            {/* Tile 3: Leave */}
            <button
              onClick={() => setActiveView('leave')}
              className={`p-2.5 rounded-xl border text-left transition-all ${
                activeView === 'leave'
                  ? 'bg-[#1C1510] border-[#FF6B1A] shadow-md shadow-[#FF6B1A]/10'
                  : 'bg-[#141414] border-[#252525] hover:border-[#383838]'
              }`}
            >
              <div className="flex items-center justify-between mb-1">
                <QrCode className={`w-4 h-4 ${activeView === 'leave' ? 'text-[#FF6B1A]' : 'text-[#7A7774]'}`} />
                <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-[#0A0A0A] text-[#FF8533] border border-[#FF8533]/30">
                  APPLY
                </span>
              </div>
              <div className="font-['Barlow_Condensed'] font-bold text-sm text-[#E8E6E3] tracking-wide leading-none mb-0.5">
                HOSTEL LEAVE
              </div>
              <div className="text-[10px] font-mono text-[#7A7774]">
                Day / Night
              </div>
            </button>

            {/* Tile 4: Gate Pass */}
            <button
              onClick={() => setActiveView('slip')}
              className={`p-2.5 rounded-xl border text-left transition-all ${
                activeView === 'slip'
                  ? 'bg-[#1C1510] border-[#FF6B1A] shadow-md shadow-[#FF6B1A]/10'
                  : 'bg-[#141414] border-[#252525] hover:border-[#383838]'
              }`}
            >
              <div className="flex items-center justify-between mb-1">
                <ShieldCheck className={`w-4 h-4 ${activeView === 'slip' ? 'text-[#FF6B1A]' : 'text-emerald-400'}`} />
                <span className="text-[9px] font-mono px-1.5 py-0.2 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/30">
                  ✔ ACTIVE
                </span>
              </div>
              <div className="font-['Barlow_Condensed'] font-bold text-sm text-[#E8E6E3] tracking-wide leading-none mb-0.5">
                GATE PASS
              </div>
              <div className="text-[10px] font-mono text-emerald-400 font-bold">
                Turnstile Code
              </div>
            </button>
          </div>

          {/* MAIN VIEWPORT: TIMETABLE */}
          {activeView === 'timetable' && (
            <div className="space-y-3">
              {/* Days Tab Strip */}
              <div className="grid grid-cols-7 gap-1 bg-[#141414] p-1 rounded-xl border border-[#252525]">
                {days.map((d) => (
                  <button
                    key={d.name}
                    onClick={() => setSelectedDay(d.name)}
                    className={`flex flex-col items-center py-1 rounded-lg transition-all ${
                      selectedDay === d.name
                        ? 'bg-[#FF6B1A] text-[#0A0A0A] font-bold shadow'
                        : 'text-[#7A7774] hover:text-[#E8E6E3]'
                    }`}
                  >
                    <span className="text-[11px] font-['Barlow_Condensed'] font-bold">{d.name}</span>
                    <span className="text-[8px] font-mono">{d.count}</span>
                  </button>
                ))}
              </div>

              {/* Class List / Content */}
              <div className="space-y-2 max-h-[350px] overflow-y-auto pr-0.5 scrollbar-thin">
                {selectedDay === 'SUN' ? (
                  <div className="h-[280px] flex flex-col items-center justify-center p-6 text-center space-y-3 rounded-xl bg-[#141414] border border-[#252525]">
                    <img
                      src="/assets/sunday_bitmoji.png"
                      alt="Sunday Relaxation Mascot"
                      className="w-28 h-28 object-contain filter drop-shadow-xl"
                      onError={(e) => {
                        (e.target as HTMLElement).style.display = 'none';
                      }}
                    />
                    <div className="space-y-1">
                      <h4 className="font-bold font-['Barlow_Condensed'] text-xl text-[#E8E6E3] tracking-wide">
                        Enjoy your Sunday<span className="text-[#FF6B1A]">.</span>
                      </h4>
                      <p className="text-xs font-mono text-[#7A7774]">
                        Zero classes • Pure relaxation
                      </p>
                    </div>
                  </div>
                ) : currentClasses.length === 0 ? (
                  <div className="h-[240px] flex items-center justify-center text-center p-6 rounded-xl bg-[#141414] border border-[#252525]">
                    <p className="font-mono text-xs text-[#7A7774]">No classes scheduled</p>
                  </div>
                ) : (
                  currentClasses.map((c, i) => {
                    const styles = getStatusStyles(c.status);
                    const isOnGoing = c.status === 'ON GOING';

                    return (
                      <Card
                        key={i}
                        className={`bg-[#141414] ${styles.border} p-2.5 rounded-xl border-l-4 ${
                          styles.bar
                        } hover:border-[#FF6B1A]/40 transition-colors`}
                      >
                        <div className="flex items-center justify-between mb-0.5">
                          <span className="font-bold text-[#E8E6E3] font-['Barlow_Condensed'] text-base tracking-wide">
                            {c.code}
                          </span>
                          <div className="flex items-center gap-1.5">
                            <span className="text-[9px] font-mono text-[#7A7774] border border-[#252525] px-1.5 py-0.2 rounded">
                              {c.type}
                            </span>
                            <span className={`text-[9px] font-mono px-1.5 py-0.2 rounded border ${styles.pill}`}>
                              {c.status}
                            </span>
                          </div>
                        </div>

                        <div className="flex items-center gap-2 text-[11px] font-mono mb-0.5">
                          <span className={isOnGoing ? 'text-emerald-400 font-bold' : 'text-[#7A7774]'}>
                            {c.time}
                          </span>
                          <span className="text-[#3F3F46]">•</span>
                          <span className="text-[#E8E6E3] font-bold">{c.room}</span>
                        </div>

                        <div className="text-[10px] font-mono text-[#52525B] truncate">
                          {c.faculty}
                        </div>
                      </Card>
                    );
                  })
                )}
              </div>
            </div>
          )}

          {/* MAIN VIEWPORT: EXAM RADAR */}
          {activeView === 'exams' && (
            <div className="space-y-2.5 max-h-[390px] overflow-y-auto pr-0.5">
              <div className="flex items-center justify-between px-1 text-[11px] font-mono text-[#7A7774]">
                <span>OFFLINE DATESHEET</span>
                <span className="text-[#818CF8]">3 EXAMS RECORDED</span>
              </div>

              {examData.map((e, idx) => (
                <Card key={idx} className="bg-[#141414] border-[#252525] p-3 rounded-xl border-l-4 border-l-[#818CF8] hover:border-[#818CF8]/50 transition-colors">
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-bold text-[#E8E6E3] font-['Barlow_Condensed'] text-base tracking-wide">
                      {e.code}
                    </span>
                    <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-[#818CF8]/15 border border-[#818CF8]/40 text-[#818CF8]">
                      {e.daysLeft}
                    </span>
                  </div>

                  <div className="text-xs text-[#A1A1AA] font-medium mb-1.5 truncate">
                    {e.name}
                  </div>

                  <div className="flex items-center justify-between pt-1 border-t border-[#222222] text-[10px] font-mono">
                    <div className="flex items-center gap-1.5 text-[#FF8533]">
                      <Clock className="w-3 h-3" />
                      <span>{e.date} • {e.time.split(' - ')[0]}</span>
                    </div>
                    <div className="text-emerald-400 font-bold">
                      {e.room} (Seat {e.seat})
                    </div>
                  </div>
                </Card>
              ))}
            </div>
          )}

          {/* MAIN VIEWPORT: APPLY LEAVE */}
          {activeView === 'leave' && (
            <div className="space-y-3 max-h-[390px] overflow-y-auto pr-0.5 text-xs">
              {/* Leave Type Toggle */}
              <div>
                <span className="text-[10px] font-mono uppercase tracking-wider text-[#7A7774] block mb-1">
                  Leave Type
                </span>
                <div className="grid grid-cols-2 gap-1.5 bg-[#141414] p-1 rounded-xl border border-[#252525]">
                  <button
                    onClick={() => setSelectedLeaveType('day')}
                    className={`py-1.5 rounded-lg text-xs font-mono font-bold transition-all ${
                      selectedLeaveType === 'day'
                        ? 'bg-[#FF6B1A] text-[#0A0A0A]'
                        : 'text-[#7A7774] hover:text-[#E8E6E3]'
                    }`}
                  >
                    Day Leave (Standard)
                  </button>
                  <button
                    onClick={() => setSelectedLeaveType('night')}
                    className={`py-1.5 rounded-lg text-xs font-mono font-bold transition-all ${
                      selectedLeaveType === 'night'
                        ? 'bg-[#FF6B1A] text-[#0A0A0A]'
                        : 'text-[#7A7774] hover:text-[#E8E6E3]'
                    }`}
                  >
                    Night Leave
                  </button>
                </div>
              </div>

              {/* Visit Place Selector */}
              <div>
                <span className="text-[10px] font-mono uppercase tracking-wider text-[#7A7774] block mb-1">
                  Visit Place
                </span>
                <div className="grid grid-cols-3 gap-1.5">
                  {['Phagwara', 'Jalandhar City', 'Local Market'].map((p) => (
                    <button
                      key={p}
                      onClick={() => setSelectedPlace(p)}
                      className={`py-1 px-1.5 rounded-lg text-[10px] font-mono truncate border transition-all ${
                        selectedPlace === p
                          ? 'bg-[#FF6B1A]/20 border-[#FF6B1A] text-[#FF6B1A] font-bold'
                          : 'bg-[#141414] border-[#252525] text-[#7A7774]'
                      }`}
                    >
                      {p}
                    </button>
                  ))}
                </div>
              </div>

              {/* Curfew Alert Banner */}
              <div className="p-2.5 rounded-xl bg-[#FF6B1A]/10 border border-[#FF6B1A]/20 flex items-center gap-2 text-[10px] font-mono text-[#FF8533]">
                <Clock className="w-3.5 h-3.5 shrink-0" />
                <span>Overnight Stay • Return before 8:00 PM curfew</span>
              </div>

              {/* Reason Selector */}
              <div>
                <span className="text-[10px] font-mono uppercase tracking-wider text-[#7A7774] block mb-1">
                  Reason Quick Chips
                </span>
                <div className="flex flex-wrap gap-1">
                  {['Personal work', 'Market & Groceries', 'Medical'].map((r) => (
                    <button
                      key={r}
                      onClick={() => setSelectedReason(r)}
                      className={`py-0.5 px-2 rounded-md text-[10px] font-mono border transition-all ${
                        selectedReason === r
                          ? 'bg-[#E8E6E3] text-[#0A0A0A] font-bold border-[#E8E6E3]'
                          : 'bg-[#141414] border-[#252525] text-[#7A7774]'
                      }`}
                    >
                      {r}
                    </button>
                  ))}
                </div>
              </div>

              {/* Submit Leave Button */}
              <button
                onClick={() => setShowOtpDialog(true)}
                className="w-full py-2.5 rounded-xl bg-[#FF6B1A] text-[#0A0A0A] font-['Barlow_Condensed'] font-bold text-base tracking-wide flex items-center justify-center gap-1.5 shadow-lg shadow-[#FF6B1A]/10 hover:bg-[#FF6B1A]/90 transition-all"
              >
                <span>APPLY HOSTEL LEAVE</span>
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
          )}

          {/* MAIN VIEWPORT: GATE PASS SLIP WITH TURNSTILE BARCODE */}
          {activeView === 'slip' && (
            <div className="space-y-3 max-h-[390px] overflow-y-auto pr-0.5">
              <Card className="bg-[#141414] border-[#282828] p-3 rounded-2xl relative overflow-hidden">
                {/* Status Bar */}
                <div className="flex items-center justify-between pb-2 mb-2 border-b border-[#252525]">
                  <div>
                    <span className="text-[9px] font-mono text-[#7A7774] block uppercase">
                      LPU HOSTEL PASS
                    </span>
                    <span className="font-bold text-[#E8E6E3] font-['Barlow_Condensed'] text-sm tracking-wide">
                      BH-4 • ROOM P103
                    </span>
                  </div>
                  <Badge className="bg-emerald-500/15 text-emerald-400 border border-emerald-500/40 text-[10px] font-mono flex items-center gap-1">
                    <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
                    ✔ ACTIVATED
                  </Badge>
                </div>

                {/* Student Info & Timing */}
                <div className="space-y-1.5 text-[11px] font-mono mb-3">
                  <div className="flex items-center justify-between text-[#7A7774]">
                    <span>REG NO</span>
                    <span className="text-[#E8E6E3] font-bold">1240XXXX</span>
                  </div>
                  <div className="flex items-center justify-between text-[#7A7774]">
                    <span>LEAVE TYPE</span>
                    <span className="text-[#FF8533]">Night Leave (Outstation)</span>
                  </div>
                  <div className="flex items-center justify-between text-[#7A7774]">
                    <span>VISIT TO</span>
                    <span className="text-[#E8E6E3]">Phagwara • Personal work</span>
                  </div>
                  <div className="flex items-center justify-between text-[#7A7774]">
                    <span>OUT TIME</span>
                    <span className="text-emerald-400">15-Nov 04:30 PM</span>
                  </div>
                  <div className="flex items-center justify-between text-[#7A7774]">
                    <span>IN TIME</span>
                    <span className="text-[#FF6B1A]">16-Nov 08:00 PM</span>
                  </div>
                </div>

                {/* Anti-Screenshot Ticking Clock */}
                <div className="p-2 rounded-xl bg-[#0A0A0A] border border-[#252525] text-center mb-3">
                  <span className="text-[9px] font-mono text-[#7A7774] block uppercase tracking-widest">
                    LIVE GATE SECURITY CLOCK
                  </span>
                  <span className="font-mono font-bold text-lg text-emerald-400 tracking-wider">
                    {currentTime}
                  </span>
                </div>

                {/* Laser Turnstile Code 128 Barcode Simulation */}
                <div className="bg-[#FFFFFF] p-2.5 rounded-xl text-center relative overflow-hidden group">
                  <div className="h-12 w-full flex items-center justify-between px-1">
                    {/* Barcode lines */}
                    {[3,1,4,2,1,3,2,4,1,2,3,1,4,2,3,1,2,4,1,3,2,1,4,2,3,1,3,2,4,1,2,3,4,1,2,3,1,4,2,3,1,2,4].map((w, idx) => (
                      <div
                        key={idx}
                        className="h-full bg-black"
                        style={{ width: `${w * 2}px` }}
                      />
                    ))}
                  </div>
                  {/* Laser Scan Line Animation */}
                  <div className="absolute inset-x-0 h-0.5 bg-emerald-500 shadow-[0_0_8px_#10B981] animate-pulse top-1/2" />
                  <span className="text-[9px] font-mono font-bold text-black tracking-widest mt-1 block">
                    LPULV-2026-884920
                  </span>
                </div>
              </Card>
            </div>
          )}

          {/* PARENT OTP REQUIRED DIALOG POPUP MODAL (When clicking Apply) */}
          {showOtpDialog && (
            <div className="absolute inset-0 bg-[#0A0A0A]/90 backdrop-blur-md z-40 p-5 flex flex-col justify-center items-center text-center animate-in fade-in duration-200">
              <div className="w-12 h-12 rounded-2xl bg-[#FF6B1A]/20 border border-[#FF6B1A]/40 flex items-center justify-center text-[#FF6B1A] mb-3">
                <Lock className="w-6 h-6" />
              </div>

              <h4 className="font-['Barlow_Condensed'] font-bold text-xl text-[#E8E6E3] tracking-wide mb-1">
                PARENT OTP REQUIRED
              </h4>

              <Badge className="bg-[#FF6B1A]/20 text-[#FF6B1A] border-[#FF6B1A]/40 text-[9px] font-mono mb-3">
                FEATURE COMING SOON
              </Badge>

              <p className="text-[11px] text-[#A1A1AA] leading-relaxed mb-3 px-2">
                UMS requires Parent/Guardian OTP verification to approve leave applications for your hostel account (sent to <code className="text-[#E8E6E3] font-mono">xxxxxx7900</code>).
              </p>

              <p className="text-[10px] text-[#71717A] leading-relaxed mb-4 px-2">
                Direct in-app OTP authorization is currently in development and coming soon in a future update! In the meantime, please submit this leave application via the official UMS portal.
              </p>

              <div className="w-full space-y-2">
                <a
                  href="https://ums.lpu.in"
                  target="_blank"
                  rel="noreferrer"
                  className="w-full py-2.5 rounded-xl bg-[#FF6B1A] text-[#0A0A0A] font-['Barlow_Condensed'] font-bold text-base tracking-wide flex items-center justify-center gap-2 hover:bg-[#FF6B1A]/90 transition-all block"
                >
                  <ExternalLink className="w-4 h-4" />
                  <span>OPEN UMS PORTAL</span>
                </a>

                <button
                  onClick={() => setShowOtpDialog(false)}
                  className="w-full py-2 text-xs font-mono text-[#7A7774] hover:text-[#E8E6E3] transition-colors"
                >
                  DISMISS
                </button>
              </div>
            </div>
          )}

          {/* Bottom Bar indicator */}
          <div className="w-32 h-1 bg-[#252525] rounded-full mx-auto mt-4" />
        </div>
      </div>
    </section>
  );
};
