import React, { useState } from 'react';
import { Badge } from '@/components/ui/badge';

export const WidgetPreview: React.FC = () => {
  const [widgetDay, setWidgetDay] = useState<string>('THU');

  const widgetDays = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];

  const widgetClasses: Record<string, { code: string; time: string; room: string; type: string; status: 'P' | 'A' | 'DL' | '' }[]> = {
    MON: [
      { code: 'CSE408', time: '10:20-11:10', room: '34-101', type: 'LEC', status: 'P' },
      { code: 'INT252', time: '11:10-12:00', room: '33-507Y', type: 'PRAC', status: 'P' },
      { code: 'PEA306', time: '14:40-15:30', room: '34-101', type: 'LEC', status: '' },
    ],
    TUE: [
      { code: 'MKT311', time: '09:30-10:20', room: '37-905', type: 'PRAC', status: 'P' },
      { code: 'INT252', time: '12:50-13:40', room: '33-507Y', type: 'PRAC', status: 'A' },
      { code: 'CSE408', time: '15:30-16:20', room: '34-101', type: 'LEC', status: '' },
    ],
    WED: [
      { code: 'INT252', time: '10:20-11:10', room: '33-507Y', type: 'PRAC', status: 'P' },
      { code: 'INT252', time: '11:10-12:00', room: '33-507Y', type: 'PRAC', status: 'P' },
      { code: 'PEA306', time: '13:40-14:30', room: '34-101', type: 'LEC', status: 'P' },
      { code: 'MKT311', time: '14:40-15:30', room: '37-905', type: 'PRAC', status: 'DL' },
    ],
    THU: [
      { code: 'INT252', time: '10:20-11:10', room: '33-507Y', type: 'PRAC', status: 'P' },
      { code: 'INT252', time: '11:10-12:00', room: '33-507Y', type: 'PRAC', status: 'P' },
      { code: 'MKT311', time: '12:50-13:40', room: '37-905', type: 'PRAC', status: '' },
      { code: 'CSE408', time: '13:40-14:30', room: '34-101', type: 'LEC', status: 'P' },
      { code: 'PEA306', time: '16:10-17:00', room: '34-101', type: 'LEC', status: 'P' },
    ],
    FRI: [
      { code: 'CSE408', time: '09:30-10:20', room: '34-101', type: 'LEC', status: 'P' },
      { code: 'INT252', time: '10:20-11:10', room: '33-507Y', type: 'PRAC', status: 'P' },
      { code: 'INT252', time: '11:10-12:00', room: '33-507Y', type: 'PRAC', status: 'P' },
      { code: 'MKT311', time: '12:50-13:40', room: '37-905', type: 'PRAC', status: '' },
      { code: 'PEA306', time: '14:40-15:30', room: '34-101', type: 'LEC', status: '' },
    ],
    SAT: [
      { code: 'PEA306', time: '09:30-10:20', room: '34-101', type: 'LEC', status: 'P' },
      { code: 'MKT311', time: '11:10-12:00', room: '37-905', type: 'PRAC', status: '' },
    ],
    SUN: [],
  };

  const statusColor = (s: string) => {
    switch (s) {
      case 'P': return { dot: 'bg-emerald-500', text: 'text-emerald-400' };
      case 'A': return { dot: 'bg-red-500', text: 'text-red-400' };
      case 'DL': return { dot: 'bg-sky-400', text: 'text-sky-400' };
      default: return { dot: 'bg-[#3A3A3A]', text: 'text-[#7A7774]' };
    }
  };

  const statusLabel = (s: string) => {
    switch (s) {
      case 'P': return 'P';
      case 'A': return 'A';
      case 'DL': return 'DL';
      default: return '•';
    }
  };

  const classes = widgetClasses[widgetDay] || [];

  return (
    <section id="widget" className="py-24 px-6 max-w-7xl mx-auto border-t border-[#252525]">
      <div className="text-center mb-16 space-y-4">
        <Badge variant="secondary" className="bg-[#FF6B1A]/10 text-[#FF6B1A] border-[#FF6B1A]/30 font-mono text-xs">
          HOME SCREEN GLANCE WIDGET
        </Badge>
        <h2 className="text-4xl sm:text-5xl font-bold font-['Barlow_Condensed'] text-[#E8E6E3] tracking-wide">
          YOUR SCHEDULE, AT A GLANCE<span className="text-[#FF6B1A]">.</span>
        </h2>
        <p className="text-[#7A7774] text-lg max-w-2xl mx-auto">
          A resizable Jetpack Glance widget that sits on your home screen. See today's classes, attendance status, and room numbers — without ever opening the app.
        </p>
      </div>

      <div className="flex justify-center">
        {/* Widget Frame — simulates home screen placement */}
        <div className="w-full max-w-[520px]">
          {/* Fake wallpaper background with blur */}
          <div className="relative rounded-3xl bg-gradient-to-br from-[#1a1520] via-[#0d1117] to-[#0a1628] p-6 border border-[#252525] shadow-2xl overflow-hidden">
            {/* Subtle wallpaper glow effects */}
            <div className="absolute top-0 left-0 w-40 h-40 bg-purple-900/20 rounded-full blur-[80px]" />
            <div className="absolute bottom-0 right-0 w-56 h-56 bg-blue-900/15 rounded-full blur-[100px]" />

            {/* Widget container */}
            <div className="relative bg-[#0A0A0A]/95 backdrop-blur-sm rounded-2xl border border-[#252525] overflow-hidden">
              {/* Widget Header */}
              <div className="flex items-center justify-between px-4 pt-4 pb-2">
                <div className="flex items-center gap-2">
                  <div className="w-5 h-5 rounded bg-[#FF6B1A]/15 border border-[#FF6B1A]/30 flex items-center justify-center">
                    <span className="text-[8px] font-bold text-[#FF6B1A] font-['Barlow_Condensed']">S</span>
                  </div>
                  <span className="font-['Barlow_Condensed'] font-bold text-sm text-[#FF6B1A] tracking-wider">
                    SKED.
                  </span>
                </div>
                <span className="text-[9px] font-mono text-[#7A7774]">
                  {widgetDay === 'SUN' ? 'SUNDAY' : `${widgetDay} • ${classes.length} CLASSES`}
                </span>
              </div>

              {/* Day Tabs */}
              <div className="flex gap-0.5 px-3 pb-3">
                {widgetDays.map((d) => (
                  <button
                    key={d}
                    onClick={() => setWidgetDay(d)}
                    className={`flex-1 py-1.5 rounded-md text-center transition-all text-[10px] font-['Barlow_Condensed'] font-bold tracking-wide ${
                      widgetDay === d
                        ? 'bg-[#FF6B1A] text-[#0A0A0A] shadow-lg shadow-[#FF6B1A]/20'
                        : 'text-[#7A7774] hover:text-[#E8E6E3] hover:bg-[#141414]'
                    }`}
                  >
                    {d}
                  </button>
                ))}
              </div>

              {/* Class Rows */}
              <div className="px-3 pb-3 space-y-1">
                {widgetDay === 'SUN' ? (
                  <div className="flex flex-col items-center justify-center py-8 space-y-3">
                    <img
                      src="/assets/sunday_bitmoji.png"
                      alt="Sunday Relaxation"
                      className="w-20 h-20 object-contain filter drop-shadow-lg"
                      onError={(e) => {
                        (e.target as HTMLElement).style.display = 'none';
                      }}
                    />
                    <div className="text-center">
                      <p className="font-['Barlow_Condensed'] font-bold text-base text-[#E8E6E3]">
                        Enjoy your Sunday<span className="text-[#FF6B1A]">.</span>
                      </p>
                      <p className="text-[9px] font-mono text-[#7A7774]">No classes • Relax & recharge</p>
                    </div>
                  </div>
                ) : classes.length === 0 ? (
                  <div className="py-8 text-center">
                    <p className="font-mono text-xs text-[#7A7774]">No classes scheduled</p>
                  </div>
                ) : (
                  classes.map((c, i) => {
                    const sc = statusColor(c.status);
                    return (
                      <div
                        key={i}
                        className="flex items-center gap-2.5 bg-[#141414] rounded-lg px-3 py-2 border border-[#1F1F1F] hover:border-[#FF6B1A]/20 transition-colors"
                      >
                        {/* Status dot */}
                        <div className={`w-2 h-2 rounded-full ${sc.dot} flex-shrink-0`} />

                        {/* Course info */}
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-1.5">
                            <span className="font-['Barlow_Condensed'] font-bold text-xs text-[#E8E6E3] tracking-wide">
                              {c.code}
                            </span>
                            <span className="text-[7px] font-mono text-[#7A7774] border border-[#252525] px-1 rounded">
                              {c.type}
                            </span>
                          </div>
                          <span className="text-[9px] font-mono text-[#7A7774]">
                            {c.time} • {c.room}
                          </span>
                        </div>

                        {/* Status label */}
                        <span className={`text-[9px] font-mono font-bold ${sc.text} flex-shrink-0`}>
                          {statusLabel(c.status)}
                        </span>
                      </div>
                    );
                  })
                )}
              </div>
            </div>

            {/* Home screen hint labels */}
            <div className="flex items-center justify-center gap-6 mt-4">
              <div className="w-10 h-10 rounded-2xl bg-[#252525]/50 border border-[#333]/30" />
              <div className="w-10 h-10 rounded-2xl bg-[#252525]/50 border border-[#333]/30" />
              <div className="w-10 h-10 rounded-2xl bg-[#252525]/50 border border-[#333]/30" />
              <div className="w-10 h-10 rounded-2xl bg-[#252525]/50 border border-[#333]/30" />
              <div className="w-10 h-10 rounded-2xl bg-[#252525]/50 border border-[#333]/30" />
            </div>
          </div>

          {/* Caption */}
          <p className="text-center text-[11px] font-mono text-[#7A7774] mt-4">
            Resizable from 4×2 to 4×5 • Auto-syncs every 15 minutes via WorkManager
          </p>
        </div>
      </div>
    </section>
  );
};
