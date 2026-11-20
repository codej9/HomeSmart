const themeButton = document.getElementById("theme-toggle");
const themeIcon = document.getElementById("theme-icon");
const themeLabel = document.getElementById("theme-label");
const iconPath = themeIcon.getAttribute("src").replace(/[^/]*$/, "");

function updateTheme() {
    const isLight = document.body.classList.contains("light-mode");

    themeLabel.textContent = isLight ? "Dark mode" : "Light mode";
    themeIcon.src = iconPath + (isLight ? "moon-stars-duotone.svg" : "sun-duotone.svg");
    themeButton.setAttribute("aria-label",
        isLight ? "Switch to dark mode" : "Switch to light mode");
    themeButton.setAttribute("aria-pressed", String(isLight));
}

if (localStorage.getItem("homesmart-theme") === "light") {
    document.body.classList.add("light-mode");
}

updateTheme();

themeButton.addEventListener("click", function () {
    document.body.classList.toggle("light-mode");

    localStorage.setItem(
        "homesmart-theme",
        document.body.classList.contains("light-mode") ? "light" : "dark"
    );

    updateTheme();
});
