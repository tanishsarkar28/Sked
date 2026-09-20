import React, { useState } from 'react';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { RefreshCw, LayoutGrid, LogOut, ChevronRight } from 'lucide-react';

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

  const thursdayClasses = [
    {
      code: 'INT252',
      name: 'Web App Dev with ReactJS',
      time: '10:20-11:10',
      room: '33-507Y',
      type: 'PRAC',
      status: 'PRESENT',
      perc: '96%',
    },
    {
      code: 'INT252',
      name: 'Web App Dev with ReactJS',
      time: '11:10-12:00',
      room: '33-507Y',
      type: 'PRAC',
      status: 'PRESENT',
      perc: '96%',
    },
    {
      code: 'MKT311',
      name: 'Digital Marketing',
      time: '12:50-13:40',
      room: '37-905',
      type: 'PRAC',
      status: 'NOT MKD',
      perc: '94%',
    },
    {
      code: 'CSE408',
      name: 'Design & Analysis of Algorithms',
      time: '13:40-14:30',
      room: '34-101',
      type: 'LEC',
      status: 'PRESENT',
      perc: '93%',
    },
    {
      code: 'PEA306',
      name: 'Analytical Skills-II',
      time: '16:10-17:00',
      room: '34-101',
      type: 'LEC',
      status: 'PRESENT',
      perc: '100%',
    },
  ];

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
                REG: XXXXXXXX
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
            ) : selectedDay === 'SAT' ? (
              <div className="space-y-2.5">
                <Card className="bg-[#141414] border-[#252525] p-3.5 rounded-xl border-l-4 border-l-emerald-500">
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-bold text-[#E8E6E3] text-sm">PEAS01</span>
                    <span className="text-[9px] font-mono text-emerald-400 bg-emerald-500/10 px-1.5 py-0.5 rounded border border-emerald-500/30">PRESENT</span>
                  </div>
                  <div className="text-[11px] text-[#7A7774] font-mono">09:30-10:20 • Block 34</div>
                </Card>
                <Card className="bg-[#141414] border-[#252525] p-3.5 rounded-xl border-l-4 border-l-[#7A7774]">
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-bold text-[#E8E6E3] text-sm">MKT311</span>
                    <span className="text-[9px] font-mono text-[#7A7774] bg-[#252525] px-1.5 py-0.5 rounded">NOT MKD</span>
                  </div>
                  <div className="text-[11px] text-[#7A7774] font-mono">11:10-12:00 • Block 37</div>
                </Card>
              </div>
            ) : (
              thursdayClasses.map((c, i) => (
                <Card
                  key={i}
                  className={`bg-[#141414] border-[#252525] p-3 rounded-xl border-l-4 ${
                    c.status === 'PRESENT' ? 'border-l-emerald-500' : 'border-l-[#7A7774]'
                  } hover:border-[#FF6B1A]/40 transition-colors`}
                >
                  <div className="flex items-center justify-between mb-0.5">
                    <div className="flex items-center gap-2">
                      <span className="font-bold text-[#E8E6E3] font-['Barlow_Condensed'] text-base tracking-wide">
                        {c.code}
                      </span>
                      <span className="text-[9px] font-mono text-[#7A7774] border border-[#252525] px-1 rounded">
                        {c.type}
                      </span>
                    </div>
                    <span
                      className={`text-[9px] font-mono px-1.5 py-0.5 rounded border ${
                        c.status === 'PRESENT'
                          ? 'text-emerald-400 bg-emerald-500/10 border-emerald-500/30'
                          : 'text-[#7A7774] bg-[#1A1A1A] border-[#252525]'
                      }`}
                    >
                      {c.status}
                    </span>
                  </div>

                  <div className="flex items-center justify-between text-[11px] text-[#7A7774]">
                    <span>{c.time} • {c.room}</span>
                    <span className="text-emerald-400 font-mono flex items-center gap-0.5">
                      {c.perc} <ChevronRight className="w-3 h-3" />
                    </span>
                  </div>
                </Card>
              ))
            )}
          </div>

          {/* Bottom Bar indicator */}
          <div className="w-32 h-1 bg-[#252525] rounded-full mx-auto mt-4" />
        </div>
      </div>
    </section>
  );
};
