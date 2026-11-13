(() => {
  const list = document.querySelector('.sessions');
  if (!list) return;

  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const sessions = [...list.querySelectorAll('.session')].map((element) => ({
    element,
    summary: element.querySelector('.session-summary'),
    content: element.querySelector('.session-content'),
    expanded: element.open,
    animation: null
  }));

  function finish(session) {
    session.animation?.cancel();
    session.animation = null;
    session.element.open = session.expanded;
    session.element.style.height = '';
    session.element.style.overflow = '';
  }

  function setExpanded(session, expanded) {
    if (session.expanded === expanded) return;
    const startHeight = session.element.getBoundingClientRect().height;
    session.animation?.cancel();
    session.expanded = expanded;
    session.summary.setAttribute('aria-expanded', String(expanded));
    session.content.inert = !expanded;

    if (reducedMotion.matches || !session.element.animate) {
      finish(session);
      return;
    }

    // Keep closing content rendered until the height transition completes.
    session.element.style.height = `${startHeight}px`;
    session.element.style.overflow = 'hidden';
    session.element.open = true;
    const endHeight = session.summary.getBoundingClientRect().height
      + (expanded ? session.content.getBoundingClientRect().height : 0);
    const animation = session.element.animate(
      { height: [`${startHeight}px`, `${endHeight}px`] },
      { duration: 300, easing: 'cubic-bezier(.22,.8,.2,1)' }
    );
    session.animation = animation;
    animation.onfinish = () => {
      if (session.animation === animation) finish(session);
    };
  }

  function close(session) {
    session.summary.focus({ preventScroll: true });
    setExpanded(session, false);
  }

  for (const session of sessions) {
    session.summary.setAttribute('aria-expanded', String(session.expanded));
    session.content.inert = !session.expanded;
    session.summary.addEventListener('click', (event) => {
      event.preventDefault();
      const expanded = !session.expanded;
      if (expanded) {
        for (const other of sessions) {
          if (other !== session) setExpanded(other, false);
        }
      }
      setExpanded(session, expanded);
    });
    session.content.querySelector('.session-close').addEventListener('click', () => close(session));
    session.element.addEventListener('keydown', (event) => {
      if (event.key === 'Escape' && session.expanded) {
        event.preventDefault();
        close(session);
      }
    });
  }

  // A changed layout needs natural heights, not the previous width's measurements.
  const finishAnimations = () => sessions.filter((session) => session.animation).forEach(finish);
  window.addEventListener('resize', finishAnimations);
  reducedMotion.addEventListener('change', finishAnimations);
  list.classList.add('has-session-interactions');
})();
