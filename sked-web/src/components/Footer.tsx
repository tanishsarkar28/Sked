import React from 'react';
import { Heart } from 'lucide-react';
import { InstagramIcon, LinkedInIcon, GitHubIcon } from './BrandIcons';

export const Footer: React.FC = () => {
  return (
    <footer className="border-t border-white/[0.08] bg-[#0A0A0A]/80 backdrop-blur-xl py-12 px-6 shadow-[inset_0_1px_1px_rgba(255,255,255,0.06)]">
      <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-6">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-gradient-to-br from-[#FF6B1A]/20 to-[#FF6B1A]/5 border border-[#FF6B1A]/35 shadow-[inset_0_1px_1px_rgba(255,255,255,0.2)] flex items-center justify-center font-bold text-[#FF6B1A] font-['Barlow_Condensed'] text-xl">
            S
          </div>
          <span className="font-['Barlow_Condensed'] font-bold text-2xl tracking-wider text-[#FF6B1A]">
            SKED<span className="text-[#E8E6E3]">.</span>
          </span>
          <span className="text-xs text-[#7A7774] font-mono">
            © 2026 • LPU Timetable & Widget Companion
          </span>
        </div>

        <div className="flex items-center gap-6">
          <a
            href="https://www.instagram.com/tanishsarkar28/"
            target="_blank"
            rel="noreferrer"
            className="text-[#7A7774] hover:text-[#E1306C] transition-colors"
            aria-label="Instagram"
          >
            <InstagramIcon size={20} />
          </a>
          <a
            href="https://www.linkedin.com/in/tanish-sarkar28/"
            target="_blank"
            rel="noreferrer"
            className="text-[#7A7774] hover:text-[#0A66C2] transition-colors"
            aria-label="LinkedIn"
          >
            <LinkedInIcon size={20} />
          </a>
          <a
            href="https://github.com/tanishsarkar28"
            target="_blank"
            rel="noreferrer"
            className="text-[#7A7774] hover:text-white transition-colors"
            aria-label="GitHub"
          >
            <GitHubIcon size={20} />
          </a>
        </div>
      </div>
    </footer>
  );
};
