const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
const menuButton = document.querySelector(".menu-toggle");
const siteLinks = document.querySelector(".site-nav");

function setMenu(open) {
  menuButton.setAttribute("aria-expanded", String(open));
  menuButton.textContent = open ? "Close" : "Menu";
  siteLinks.classList.toggle("open", open);
}

menuButton.addEventListener("click", () => {
  setMenu(menuButton.getAttribute("aria-expanded") !== "true");
});

siteLinks.addEventListener("click", (event) => {
  if (event.target.closest("a")) setMenu(false);
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && menuButton.getAttribute("aria-expanded") === "true") {
    setMenu(false);
    menuButton.focus();
  }
});

const revealItems = document.querySelectorAll("[data-reveal]");

if (!reducedMotion.matches && "IntersectionObserver" in window) {
  document.body.classList.add("can-animate");
  const revealObserver = new IntersectionObserver((entries, observer) => {
    entries.forEach((entry) => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add("is-visible");
      observer.unobserve(entry.target);
    });
  }, { threshold: 0.12, rootMargin: "0px 0px -40px" });

  revealItems.forEach((item) => revealObserver.observe(item));
}

const hero = document.querySelector(".hero");
const heroVisual = document.querySelector(".hero-visual");
let scrollFrame = null;

function moveHero() {
  const distance = Math.min(window.scrollY, hero.offsetHeight);
  heroVisual.style.setProperty("--hero-shift", `${distance * 0.07}px`);
  heroVisual.style.setProperty("--hero-tilt", `${Math.min(distance * 0.003, 1.8)}deg`);
  scrollFrame = null;
}

if (!reducedMotion.matches) {
  window.addEventListener("scroll", () => {
    if (scrollFrame === null) scrollFrame = window.requestAnimationFrame(moveHero);
  }, { passive: true });
}

const featureInputs = document.querySelectorAll('input[name="feature"]');
const permissionList = document.querySelector("#permission-list");

function updatePermissions() {
  const selected = new Set(
    Array.from(featureInputs).filter((input) => input.checked).map((input) => input.value)
  );
  const permissions = [];

  if (selected.has("automatic") || selected.has("run-metrics")) {
    permissions.push("Activity recognition — for sensor steps and cadence.");
  }
  if (selected.has("automatic") || selected.has("reminder")) {
    permissions.push("Notifications — for background tracking or daily reminders.");
  }
  if (permissions.length === 0) {
    permissions.push("No permissions for manual tracking and timer-only runs.");
  }

  permissionList.replaceChildren();
  permissions.forEach((permission) => {
    const item = document.createElement("li");
    item.textContent = permission;
    permissionList.append(item);
  });
}

featureInputs.forEach((input) => input.addEventListener("change", updatePermissions));
