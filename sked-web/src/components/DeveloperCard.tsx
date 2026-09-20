import React from 'react';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { ExternalLink, Heart } from 'lucide-react';
import { InstagramIcon, LinkedInIcon, GitHubIcon } from './BrandIcons';

export const DeveloperCard: React.FC = () => {
  const socials = [
    {
      name: 'Instagram',
      handle: '@tanishsarkar28',
      url: 'https://www.instagram.com/tanishsarkar28/',
      icon: InstagramIcon,
      color: '#E1306C',
    },
    {
      name: 'LinkedIn',
      handle: 'in/tanish-sarkar28',
      url: 'https://www.linkedin.com/in/tanish-sarkar28/',
      icon: LinkedInIcon,
      color: '#0A66C2',
    },
    {
      name: 'GitHub',
      handle: '@tanishsarkar28',
      url: 'https://github.com/tanishsarkar28',
      icon: GitHubIcon,
      color: '#FFFFFF',
    },
  ];

  return (
    <section id="developer" className="py-24 px-6 max-w-4xl mx-auto border-t border-[#252525]">
      <div className="text-center mb-12 space-y-3">
        <Badge variant="secondary" className="bg-[#FF6B1A]/10 text-[#FF6B1A] border-[#FF6B1A]/30 font-mono text-xs">
          CREATOR & MAINTAINER
        </Badge>
        <h2 className="text-4xl font-bold font-['Barlow_Condensed'] text-[#E8E6E3] tracking-wide">
          MEET THE DEVELOPER<span className="text-[#FF6B1A]">.</span>
        </h2>
        <p className="text-[#7A7774] text-sm max-w-xl mx-auto">
          Crafted with care by an LPU Computer Science student to give peers a clutter-free, lighting-fast timetable experience.
        </p>
      </div>

      <Card className="bg-[#141414] border-[#252525] p-8 rounded-2xl shadow-2xl relative overflow-hidden">
        <div className="flex flex-col sm:flex-row items-center sm:items-start gap-6 mb-8 text-center sm:text-left">
          {/* Monogram Avatar */}
          <div className="w-20 h-20 rounded-2xl bg-[#FF6B1A]/15 border border-[#FF6B1A]/40 flex items-center justify-center text-[#FF6B1A] font-['Barlow_Condensed'] font-bold text-3xl shadow-inner shrink-0">
            TS
          </div>

          <div className="space-y-1.5 flex-1">
            <div className="flex flex-wrap items-center justify-center sm:justify-start gap-2.5">
              <h3 className="text-2xl font-bold font-['Barlow_Condensed'] text-[#E8E6E3] tracking-wide">
                TANISH SARKAR
              </h3>
              <span className="text-xs font-mono text-[#FF6B1A] border border-[#FF6B1A]/30 bg-[#FF6B1A]/10 px-2 py-0.5 rounded">
                B.TECH CSE • LPU
              </span>
            </div>
            <p className="text-sm text-[#7A7774] max-w-md">
              Full-Stack & Mobile Developer specializing in high-performance Android Jetpack Compose, Flutter, and minimalist design systems.
            </p>
          </div>
        </div>

        {/* Social Links Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {socials.map((s, idx) => (
            <a
              key={idx}
              href={s.url}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center justify-between p-4 rounded-xl bg-[#0D0D0D] border border-[#252525] hover:border-[#FF6B1A]/50 hover:bg-[#1A1A1A] transition-all group"
            >
              <div className="flex items-center gap-3">
                <div 
                  className="w-10 h-10 rounded-lg flex items-center justify-center transition-transform group-hover:scale-105"
                  style={{ backgroundColor: `${s.color}18`, border: `1px solid ${s.color}35` }}
                >
                  <s.icon className="w-5 h-5" style={{ color: s.color }} />
                </div>
                <div className="text-left">
                  <div className="text-sm font-bold font-['Barlow_Condensed'] text-[#E8E6E3] group-hover:text-[#FF6B1A] transition-colors">
                    {s.name}
                  </div>
                  <div className="text-xs font-mono text-[#7A7774]">
                    {s.handle}
                  </div>
                </div>
              </div>
              <ExternalLink className="w-4 h-4 text-[#7A7774] group-hover:text-[#E8E6E3] transition-colors" />
            </a>
          ))}
        </div>

        {/* Footer note inside card */}
        <div className="mt-8 pt-6 border-t border-[#252525] flex flex-col sm:flex-row items-center justify-between gap-4 text-xs font-mono text-[#7A7774]">
          <span className="flex items-center gap-1.5">
            Built with <Heart className="w-3.5 h-3.5 text-[#FF6B1A] fill-[#FF6B1A]" /> for Lovely Professional University
          </span>
          <span className="text-[#E8E6E3]">
            OPEN SOURCE • ZERO TRACKING
          </span>
        </div>
      </Card>
    </section>
  );
};
