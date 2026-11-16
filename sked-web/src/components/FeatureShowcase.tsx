import React from 'react';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { 
  CalendarClock, 
  GraduationCap, 
  ShieldCheck, 
  QrCode, 
  WifiOff,
  BellRing,
  LayoutGrid,
  Zap,
  Coffee
} from 'lucide-react';

export const FeatureShowcase: React.FC = () => {
  const features = [
    {
      icon: CalendarClock,
      title: "LIVE DEPARTURES BOARD",
      tag: "REAL-TIME",
      desc: "Airport departures-board layout. Shows ongoing, upcoming, and finished classes with real-time NOW. spotlight, room numbers, teacher details, and course codes.",
    },
    {
      icon: LayoutGrid,
      title: "2×2 QUICK ACCESS DASHBOARD",
      tag: "V1.6 REDESIGN",
      desc: "High-speed departures dashboard launching Timetable, Exam Radar, Hostel Leave, and Gate Pass Slip with live dynamic badges and 1-tap rapid navigation.",
    },
    {
      icon: QrCode,
      title: "HOSTEL LEAVE APPLICATION",
      tag: "ANDROID & iOS",
      desc: "Apply for Day, Night, and Extended hostel leaves in seconds. Features 15-minute slot pickers, address quick chips, curfew alerts, and smart Parent OTP portal bridge.",
    },
    {
      icon: ShieldCheck,
      title: "DIGITAL GATE PASS & BARCODE",
      tag: "LASER TURNSTILE",
      desc: "Official hostel gate pass with live activation status (✔ Activated), high-density Code 128 laser barcode scanner compatibility, student photo, and anti-screenshot ticking timer.",
    },
    {
      icon: BellRing,
      title: "AUTOMATED MAKEUP CLASS RADAR",
      tag: "BACKGROUND SYNC",
      desc: "Periodic background WorkManager sync checks UMS every 2 hours with quiet hours protection. Sends instant push alerts whenever instructors add, reschedule, or cancel makeup sessions.",
    },
    {
      icon: GraduationCap,
      title: "EXAM DATESHEET & SEATING",
      tag: "LIVE RADAR",
      desc: "Complete MTE, ETE, and Practical examination timetable with live countdown radars, reporting hours, and room seating allocations cached 100% offline.",
    },
    {
      icon: Zap,
      title: "ULTRA-FAST 1.5S PARALLEL SYNC",
      tag: "CONCURRENT",
      desc: "Concurrently fetches timetable, classic datesheet, leave balances, and gate pass status in parallel — cutting sync latency from 8+ seconds down to ~1.5s.",
    },
    {
      icon: ShieldCheck,
      title: "LPU TOUCH COMPATIBLE",
      tag: "ZERO CONFLICT",
      desc: "Pure Web UMS scraping with zero mobile webservice calls. Keeps your official LPU Touch mobile app permanently logged in without single-device session invalidations.",
    },
    {
      icon: WifiOff,
      title: "100% OFFLINE & PRIVATE",
      tag: "ZERO TRACKING",
      desc: "UMS servers crash during 8:30 AM to 9:30 AM morning rush. Sked caches everything locally on your device. Zero external proxy servers, zero analytics tracking, zero ads.",
    },
  ];

  return (
    <section id="features" className="py-24 px-6 max-w-7xl mx-auto border-t border-[#252525]">
      <div className="text-center mb-16 space-y-4">
        <Badge variant="secondary" className="bg-[#FF6B1A]/10 text-[#FF6B1A] border-[#FF6B1A]/30 font-mono text-xs">
          ENGINEERED FOR LPU STUDENTS
        </Badge>
        <h2 className="text-4xl sm:text-5xl font-bold font-['Barlow_Condensed'] text-[#E8E6E3] tracking-wide">
          DESIGNED FOR SPEED. ZERO SAAS CLUTTER<span className="text-[#FF6B1A]">.</span>
        </h2>
        <p className="text-[#7A7774] text-lg max-w-2xl mx-auto">
          Built with an uncompromising departures-board aesthetic: Blaze orange (<code className="text-[#FF6B1A]">#FF6B1A</code>) on Ink near-black (<code className="text-[#E8E6E3]">#0A0A0A</code>).
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {features.map((f, idx) => (
          <Card key={idx} className="bg-[#141414] border-[#252525] p-6 rounded-xl hover:border-[#FF6B1A]/40 transition-all duration-300 group">
            <div className="flex items-center justify-between mb-4">
              <div className="w-12 h-12 rounded-lg bg-[#FF6B1A]/10 border border-[#FF6B1A]/30 flex items-center justify-center text-[#FF6B1A] group-hover:scale-110 transition-transform">
                <f.icon className="w-6 h-6" />
              </div>
              <span className="text-[11px] font-mono text-[#7A7774] border border-[#252525] px-2 py-0.5 rounded">
                {f.tag}
              </span>
            </div>

            <h3 className="text-xl font-bold font-['Barlow_Condensed'] text-[#E8E6E3] mb-2 tracking-wide group-hover:text-[#FF6B1A] transition-colors">
              {f.title}
            </h3>

            <p className="text-sm text-[#7A7774] leading-relaxed">
              {f.desc}
            </p>
          </Card>
        ))}
      </div>
    </section>
  );
};
