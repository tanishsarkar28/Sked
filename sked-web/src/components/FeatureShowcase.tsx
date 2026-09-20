import React from 'react';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { 
  CalendarClock, 
  Calculator, 
  Coffee, 
  ShieldCheck, 
  Zap, 
  WifiOff 
} from 'lucide-react';

export const FeatureShowcase: React.FC = () => {
  const features = [
    {
      icon: CalendarClock,
      title: "LIVE DEPARTURES BOARD",
      tag: "REAL-TIME",
      desc: "Inspired by European airport departures boards. Shows ongoing, upcoming, and finished classes with real-time NOW. spotlight, room numbers, and teacher details.",
    },
    {
      icon: ShieldCheck,
      title: "LPU TOUCH COMPATIBLE",
      tag: "ZERO CONFLICT",
      desc: "Pure Web UMS timetable scraping with zero mobile webservice calls. Keeps your official LPU Touch mobile app permanently logged in without single-device session invalidations.",
    },
    {
      icon: Coffee,
      title: "SUNDAY BITMOJI CHILL MODE",
      tag: "ZERO STRESS",
      desc: "Sundays are meant for relaxing. The widget automatically strips all academic stress and displays a centered chilling Bitmoji mascot with 'Enjoy your Sunday.'",
    },
    {
      icon: ShieldCheck,
      title: "100% ON-DEVICE PRIVACY",
      tag: "ZERO PROXY",
      desc: "No third-party cloud servers, telemetry, or external proxies. All UMS authentication and HTML parsing happens directly inside your phone's secure storage.",
    },
    {
      icon: Zap,
      title: "BACKGROUND AUTO SYNC",
      tag: "GLANCE WIDGET",
      desc: "Android WorkManager background updates run periodically to guarantee your home screen widget stays up to date with your class schedule without opening the app.",
    },
    {
      icon: WifiOff,
      title: "100% OFFLINE READY",
      tag: "ALWAYS WORKS",
      desc: "UMS servers crash during 8:30 AM to 9:30 AM peak rush hours. Sked keeps your complete weekly timetable cached locally so you never miss your class room number.",
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
