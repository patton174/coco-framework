import {useEffect, useRef} from 'react';
import {gsap} from 'gsap';
import {ScrollTrigger} from 'gsap/ScrollTrigger';

/**
 * GSAP-powered documentation motion system for Coco Framework.
 * - High-precision, hardware-accelerated scroll progress tracking with spring scrub.
 * - Selective, batch-revealed heading & structural card animations (reading-first: paragraphs stay visible).
 * - Automatic context cleanup via gsap.context() across Docusaurus route transitions.
 * - Full respect for prefers-reduced-motion accessibility standard.
 */
export function useDocMotion(route: string) {
  const contentRef = useRef<HTMLDivElement>(null);
  const progressRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (typeof window === 'undefined') return;
    gsap.registerPlugin(ScrollTrigger);

    const root = contentRef.current;
    const progress = progressRef.current;
    if (!root) return;

    const preference = window.matchMedia('(prefers-reduced-motion: reduce)');
    if (preference.matches) {
      if (progress) progress.style.transform = 'scaleX(1)';
      return;
    }

    const ctx = gsap.context(() => {
      // 1. Reading progress bar with smooth spring-like scrub
      if (progress) {
        gsap.fromTo(
          progress,
          {scaleX: 0},
          {
            scaleX: 1,
            ease: 'none',
            scrollTrigger: {
              trigger: root,
              start: 'top top+=65',
              end: 'bottom bottom',
              scrub: 0.15,
              invalidateOnRefresh: true,
            },
          },
        );
      }

      // 2. Structured headings and visual cards animation
      // Target only structural blocks: H1, H2, H3, admonitions, code blocks, tables
      // Regular paragraphs remain completely static so reading is never delayed!
      const markdown = root.querySelector('.markdown');
      if (markdown) {
        const structuralTargets = markdown.querySelectorAll<HTMLElement>(
          ':scope > h1, :scope > header > h1, :scope > h2, :scope > h3, :scope > .theme-admonition, :scope > .theme-code-block, :scope > table, :scope > blockquote',
        );

        const unreachedTargets: HTMLElement[] = [];
        const viewportHeight = window.innerHeight;

        structuralTargets.forEach(target => {
          const rect = target.getBoundingClientRect();
          if (rect.top < viewportHeight && rect.bottom > 0) {
            // Above the fold: gently settle in on initial page render
            if (/^H[123]$/.test(target.tagName)) {
              gsap.from(target, {
                y: 12,
                opacity: 0.75,
                duration: 0.5,
                ease: 'power2.out',
              });
            }
          } else {
            unreachedTargets.push(target);
          }
        });

        if (unreachedTargets.length > 0) {
          ScrollTrigger.batch(unreachedTargets, {
            start: 'top 92%',
            once: true,
            onEnter: batch => {
              gsap.fromTo(
                batch,
                {opacity: 0, y: 14},
                {
                  opacity: 1,
                  y: 0,
                  duration: 0.5,
                  stagger: 0.08,
                  ease: 'power2.out',
                  overwrite: 'auto',
                },
              );
            },
          });
        }
      }
    }, root);

    // Refresh ScrollTrigger when images or dynamic fonts finish loading
    const timer = setTimeout(() => {
      ScrollTrigger.refresh();
    }, 250);

    return () => {
      clearTimeout(timer);
      ctx.revert();
    };
  }, [route]);

  return {contentRef, progressRef};
}
