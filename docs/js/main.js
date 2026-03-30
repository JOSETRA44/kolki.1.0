/* ============================================================
   KOLKI DOCS — main.js
   Features: dark mode, mobile nav, scroll animations,
             active nav highlight, sticky navbar
   ============================================================ */

(function () {
  'use strict';

  /* ── Theme (dark / light) ─────────────────────────────── */
  const html         = document.documentElement;
  const themeToggle  = document.getElementById('themeToggle');
  const STORAGE_KEY  = 'kolki-theme';

  function applyTheme(theme) {
    html.setAttribute('data-theme', theme);
    localStorage.setItem(STORAGE_KEY, theme);
  }

  // Load saved preference or system preference
  const saved = localStorage.getItem(STORAGE_KEY);
  if (saved) {
    applyTheme(saved);
  } else if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
    applyTheme('dark');
  }

  themeToggle.addEventListener('click', function () {
    const current = html.getAttribute('data-theme');
    applyTheme(current === 'dark' ? 'light' : 'dark');
  });

  /* ── Mobile Nav ───────────────────────────────────────── */
  const hamburger = document.getElementById('hamburger');
  const navLinks  = document.getElementById('navLinks');
  const body      = document.body;

  hamburger.addEventListener('click', function () {
    body.classList.toggle('nav-open');
  });

  // Close nav when a link is clicked
  navLinks.querySelectorAll('a').forEach(function (link) {
    link.addEventListener('click', function () {
      body.classList.remove('nav-open');
    });
  });

  // Close nav on outside click
  document.addEventListener('click', function (e) {
    if (!navLinks.contains(e.target) && !hamburger.contains(e.target)) {
      body.classList.remove('nav-open');
    }
  });

  /* ── Sticky Navbar (scrolled class) ──────────────────── */
  const navbar = document.getElementById('navbar');

  function onScroll() {
    if (window.scrollY > 50) {
      navbar.classList.add('scrolled');
    } else {
      navbar.classList.remove('scrolled');
    }
    updateActiveLink();
  }

  window.addEventListener('scroll', onScroll, { passive: true });

  /* ── Active Nav Link on Scroll ────────────────────────── */
  const sections  = document.querySelectorAll('section[id]');
  const navAnchors = document.querySelectorAll('.nav-links a');

  function updateActiveLink() {
    let current = '';
    sections.forEach(function (section) {
      const sectionTop = section.offsetTop - 100;
      if (window.scrollY >= sectionTop) {
        current = section.getAttribute('id');
      }
    });
    navAnchors.forEach(function (a) {
      a.classList.toggle('active', a.getAttribute('href') === '#' + current);
    });
  }

  /* ── Scroll Animations (IntersectionObserver) ─────────── */
  const animatedEls = document.querySelectorAll('.animate-on-scroll');

  const observer = new IntersectionObserver(
    function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('visible');
          observer.unobserve(entry.target); // animate once
        }
      });
    },
    { threshold: 0.12 }
  );

  animatedEls.forEach(function (el) {
    observer.observe(el);
  });

  /* ── Smooth scroll polyfill for older Androids ────────── */
  document.querySelectorAll('a[href^="#"]').forEach(function (anchor) {
    anchor.addEventListener('click', function (e) {
      const target = document.querySelector(this.getAttribute('href'));
      if (target) {
        e.preventDefault();
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    });
  });

})();
