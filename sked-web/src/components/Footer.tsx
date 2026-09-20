import React from 'react';
import { Heart } from 'lucide-react';
import { InstagramIcon, LinkedInIcon, GitHubIcon } from './BrandIcons';

export const Footer: React.FC = () => {
  return (
    <footer className="border-t border-[#252525] bg-[#0A0A0A] py-12 px-6">
      <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-center justify-between gap-6">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-[#FF6B1A]/15 border border-[#FF6B1A]/30 flex items-center justify-center font-bold text-[#FF6B1A] font-['Barlow_Condensed'] text-xl">
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
