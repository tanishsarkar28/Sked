import React, { useState } from 'react';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { RefreshCw, LayoutGrid, LogOut } from 'lucide-react';

interface MockClass {
  code: string;
  name: string;
  time: string;
  room: string;
  faculty: string;
  type: 'LEC' | 'PRAC' | 'TUT';
  status: 'OVER' | 'UPCOMING' | 'ON GOING' | 'PENDING';
}

export const LiveMockup: React.FC = () => {
  const [selectedDay, setSelectedDay] = useState<string>('THU');

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
      { code: 'INT257', name: 'Software Project Management', time: '10:20-11:10', room: '33-612', faculty: 'Akash Pundir • Sec K24RL', type: 'LEC', status: 'OVER' },
      { code: 'INT257', name: 'Software Project Management', time: '11:10-12:00', room: '33-612', faculty: 'Akash Pundir • Sec K24RL', type: 'LEC', status: 'OVER' },
      { code: 'CSE408', name: 'Design & Analysis of Algorithms', time: '13:40-14:30', room: '33-608', faculty: 'Ritesh Tiwari • Sec K24RL', type: 'LEC', status: 'OVER' },
      { code: 'CSE408', name: 'Design & Analysis of Algorithms', time: '14:30-15:20', room: '33-608', faculty: 'Ritesh Tiwari • Sec K24RL', type: 'LEC', status: 'OVER' },
    ],
    TUE: [
      { code: 'MKT311', name: 'Digital Marketing', time: '09:30-10:20', room: '37-905', faculty: 'Rohan Sharma • Sec K24RL', type: 'PRAC', status: 'OVER' },
      { code: 'INT252', name: 'Web App Dev with ReactJS', time: '12:50-13:40', room: '33-507Y', faculty: 'Akash Pundir • Sec K24RL', type: 'PRAC', status: 'ON GOING' },
      { code: 'CSE408', name: 'Design & Analysis of Algorithms', time: '15:30-16:20', room: '34-101', faculty: 'Ritesh Tiwari • Sec K24RL', type: 'LEC', status: 'UPCOMING' },
    ],
    WED: [
      { code: 'INT252', name: 'Web App Dev with ReactJS', time: '10:20-11:10', room: '33-507Y', faculty: 'Akash Pundir • Sec K24RL', type: 'PRAC', status: 'OVER' },
      { code: 'INT252', name: 'Web App Dev with ReactJS', time: '11:10-12:00', room: '33-507Y', faculty: 'Akash Pundir • Sec K24RL', type: 'PRAC', status: 'OVER' },
      { code: 'PEA306', name: 'Analytical Skills-II', time: '13:40-14:30', room: '34-101', faculty: 'Sunita Rao • Sec K24RL', type: 'LEC', status: 'ON GOING' },
      { code: 'MKT311', name: 'Digital Marketing', time: '14:40-15:30', room: '37-905', faculty: 'Rohan Sharma • Sec K24RL', type: 'PRAC', status: 'UPCOMING' },
      { code: 'CSE408', name: 'Design & Analysis of Algorithms', time: '16:10-17:00', room: '34-101', faculty: 'Ritesh Tiwari • Sec K24RL', type: 'LEC', status: 'PENDING' },
    ],
    THU: [
      { code: 'INT252', name: 'Web App Dev with ReactJS', time: '10:20-11:10', room: '33-507Y', faculty: 'Akash Pundir • Sec K24RL', type: 'PRAC', status: 'OVER' },
      { code: 'INT252', name: 'Web App Dev with ReactJS', time: '11:10-12:00', room: '33-507Y', faculty: 'Akash Pundir • Sec K24RL', type: 'PRAC', status: 'OVER' },
      { code: 'MKT311', name: 'Digital Marketing', time: '12:50-13:40', room: '37-905', faculty: 'Rohan Sharma • Sec K24RL', type: 'PRAC', status: 'ON GOING' },
      { code: 'CSE408', name: 'Design & Analysis of Algorithms', time: '13:40-14:30', room: '34-101', faculty: 'Ritesh Tiwari • Sec K24RL', type: 'LEC', status: 'UPCOMING' },
      { code: 'PEA306', name: 'Analytical Skills-II', time: '16:10-17:00', room: '34-101', faculty: 'Sunita Rao • Sec K24RL', type: 'LEC', status: 'PENDING' },
    ],
    FRI: [
      { code: 'CSE408', name: 'Design & Analysis of Algorithms', time: '09:30-10:20', room: '34-101', faculty: 'Ritesh Tiwari • Sec K24RL', type: 'LEC', status: 'OVER' },
      { code: 'INT252', name: 'Web App Dev with ReactJS', time: '10:20-11:10', room: '33-507Y', faculty: 'Akash Pundir • Sec K24RL', type: 'PRAC', status: 'OVER' },
      { code: 'INT252', name: 'Web App Dev with ReactJS', time: '11:10-12:00', room: '33-507Y', faculty: 'Akash Pundir • Sec K24RL', type: 'PRAC', status: 'OVER' },
      { code: 'MKT311', name: 'Digital Marketing', time: '12:50-13:40', room: '37-905', faculty: 'Rohan Sharma • Sec K24RL', type: 'PRAC', status: 'ON GOING' },
      { code: 'PEA306', name: 'Analytical Skills-II', time: '14:40-15:30', room: '34-101', faculty: 'Sunita Rao • Sec K24RL', type: 'LEC', status: 'UPCOMING' },
      { code: 'INT257', name: 'Software Project Management', time: '16:10-17:00', room: '33-612', faculty: 'Akash Pundir • Sec K24RL', type: 'LEC', status: 'PENDING' },
    ],
    SAT: [
      { code: 'PEA306', name: 'Analytical Skills-II', time: '09:30-10:20', room: '34-101', faculty: 'Sunita Rao • Sec K24RL', type: 'LEC', status: 'OVER' },
      { code: 'MKT311', name: 'Digital Marketing', time: '11:10-12:00', room: '37-905', faculty: 'Rohan Sharma • Sec K24RL', type: 'PRAC', status: 'UPCOMING' },
    ],
    SUN: [],
  };

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
          INTERACTIVE INTERFACE PREVIEW
        </Badge>
        <h2 className="text-4xl sm:text-5xl font-bold font-['Barlow_Condensed'] text-[#E8E6E3] tracking-wide">
          CLEAN. DEPARTURES-BOARD TYPOGRAPHY<span className="text-[#FF6B1A]">.</span>
        </h2>
        <p className="text-[#7A7774] text-lg max-w-2xl mx-auto">
          Tap any day to preview the on-device interface. Tap Sunday to see the relaxation Bitmoji widget.
        </p>
      </div>

      <div className="flex justify-center">
        {/* Phone Frame */}
        <div className="w-full max-w-[380px] bg-[#0A0A0A] rounded-[42px] border-[6px] border-[#252525] p-4 shadow-2xl relative overflow-hidden">
          {/* Dynamic Island / Speaker */}
          <div className="w-28 h-4 bg-[#141414] rounded-full mx-auto mb-4" />

          {/* Phone Header */}
          <div className="flex items-center justify-between px-2 mb-4">
            <div className="flex items-center gap-1.5">
              <span className="font-['Barlow_Condensed'] font-bold text-xl text-[#FF6B1A] tracking-wider">
                SKED.
              </span>
            </div>
            <div className="flex items-center gap-3 text-[#7A7774]">
              <RefreshCw className="w-4 h-4 hover:text-[#FF6B1A] cursor-pointer transition-colors" />
              <LayoutGrid className="w-4 h-4 hover:text-[#E8E6E3] cursor-pointer transition-colors" />
              <LogOut className="w-4 h-4 text-red-500 cursor-pointer" />
            </div>
          </div>

          {/* User Registration Row */}
          <div className="flex items-center justify-between px-2 mb-4">
            <div>
              <span className="text-[10px] font-mono font-bold text-[#7A7774] block">
                REG: 12407229
              </span>
              <span className="text-xs font-bold text-[#E8E6E3] font-['Barlow_Condensed']">
                {selectedDay === 'SUN' ? 'Sunday • Relax' : `${selectedDay} Schedule`}
              </span>
            </div>
            <span className="text-[10px] font-mono text-[#FF6B1A] bg-[#FF6B1A]/10 border border-[#FF6B1A]/30 px-2 py-0.5 rounded">
              AUTO-SYNC: 15m
            </span>
          </div>

          {/* Days Tab Strip */}
          <div className="grid grid-cols-7 gap-1 bg-[#141414] p-1.5 rounded-xl border border-[#252525] mb-4">
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
                <span className="text-[9px] font-mono">{d.count}</span>
              </button>
            ))}
          </div>

          {/* Class List / Content */}
          <div className="space-y-2.5 min-h-[360px]">
            {selectedDay === 'SUN' ? (
              <div className="h-[360px] flex flex-col items-center justify-center p-6 text-center space-y-4 rounded-xl bg-[#141414] border border-[#252525]">
                <img
                  src="/assets/sunday_bitmoji.png"
                  alt="Sunday Relaxation Mascot"
                  className="w-32 h-32 object-contain filter drop-shadow-xl"
                  onError={(e) => {
                    (e.target as HTMLElement).style.display = 'none';
                  }}
                />
                <div className="space-y-1">
                  <h4 className="font-bold font-['Barlow_Condensed'] text-xl text-[#E8E6E3] tracking-wide">
                    Enjoy your Sunday<span className="text-[#FF6B1A]">.</span>
                  </h4>
                  <p className="text-xs font-mono text-[#7A7774]">
                    Zero classes • Relax & recharge
                  </p>
                </div>
              </div>
            ) : currentClasses.length === 0 ? (
              <div className="h-[360px] flex items-center justify-center text-center p-6 rounded-xl bg-[#141414] border border-[#252525]">
                <p className="font-mono text-xs text-[#7A7774]">No classes scheduled</p>
              </div>
            ) : (
              currentClasses.map((c, i) => {
                const styles = getStatusStyles(c.status);
                const isOnGoing = c.status === 'ON GOING';

                return (
                  <Card
                    key={i}
                    className={`bg-[#141414] ${styles.border} p-3 rounded-xl border-l-4 ${
                      styles.bar
                    } hover:border-[#FF6B1A]/40 transition-colors`}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <span className="font-bold text-[#E8E6E3] font-['Barlow_Condensed'] text-base tracking-wide">
                        {c.code}
                      </span>
                      <div className="flex items-center gap-1.5">
                        <span className="text-[9px] font-mono text-[#7A7774] border border-[#252525] px-1.5 py-0.5 rounded">
                          {c.type}
                        </span>
                        <span className={`text-[9px] font-mono px-1.5 py-0.5 rounded border ${styles.pill}`}>
                          {c.status}
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center gap-2 text-[11px] font-mono mb-0.5">
                      <span className={isOnGoing ? 'text-emerald-400' : 'text-[#7A7774]'}>
                        {c.time}
                      </span>
                      <span className="text-[#3F3F46]">•</span>
                      <span className="text-[#7A7774]">{c.room}</span>
                    </div>

                    <div className="text-[10px] font-mono text-[#52525B]">
                      {c.faculty}
                    </div>
                  </Card>
                );
              })
            )}
          </div>

          {/* Bottom Bar indicator */}
          <div className="w-32 h-1 bg-[#252525] rounded-full mx-auto mt-4" />
        </div>
      </div>
    </section>
  );
};
