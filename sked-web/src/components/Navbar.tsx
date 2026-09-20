import React from 'react';

interface NavbarProps {
  onInstallClick: (platform?: 'android' | 'ios') => void;
}

export const Navbar: React.FC<NavbarProps> = ({ onInstallClick }) => {
  return (
    <header className="fixed top-0 left-0 right-0 z-50 bg-[#0A0A0A]/80 backdrop-blur-md border-b border-[#252525]">
      <div className="container mx-auto px-6 h-16 flex items-center justify-between max-w-7xl">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-lg bg-[#FF6B1A]/15 border border-[#FF6B1A]/30 flex items-center justify-center font-bold text-[#FF6B1A] font-['Barlow_Condensed'] text-xl">
            S
          </div>
          <span className="font-['Barlow_Condensed'] font-bold text-2xl tracking-wider text-[#FF6B1A]">
            SKED<span className="text-[#E8E6E3]">.</span>
          </span>
          <span className="hidden sm:inline-block text-xs font-mono text-[#7A7774] border border-[#252525] px-2 py-0.5 rounded ml-2">
            LPU COMPANION
          </span>
        </div>

        <nav className="hidden md:flex items-center gap-8 text-sm font-medium text-[#7A7774]">
          <a href="#features" className="hover:text-[#E8E6E3] transition-colors">Features</a>
          <a href="#install" className="hover:text-[#E8E6E3] transition-colors">Install Guide</a>
          <a href="#widget" className="hover:text-[#E8E6E3] transition-colors">Widget Preview</a>
          <a href="#developer" className="hover:text-[#E8E6E3] transition-colors">Developer</a>
        </nav>

      </div>
    </header>
  );
};
