const repository = "https://github.com/psyb0t/dikciz-launcher";
const baseUrl = "/dikciz-launcher/";

// Generated pages are edited through their generator, not on GitHub.
const generatedPages = new Set(["command-reference.md", "token-permissions.md"]);

export default {
  title: "Dikciz Launcher",
  tagline: "Programmable Android home and app runtime",
  url: "https://psyb0t.github.io",
  baseUrl,
  trailingSlash: true,
  future: { faster: { rspackBundler: true, swcJsLoader: true } },
  organizationName: "psyb0t",
  projectName: "dikciz-launcher",
  onBrokenLinks: "throw",
  onBrokenAnchors: "throw",
  markdown: { format: "detect", mermaid: true, hooks: { onBrokenMarkdownLinks: "throw" } },
  staticDirectories: ["static", ".assets"],
  scripts: [{ src: "https://psyb0t.github.io/analytics.js", async: true }],
  presets: [
    ["classic", {
      blog: false,
      sitemap: { ignorePatterns: [baseUrl + "search", baseUrl + "search/"] },
      theme: { customCss: ["./src/css/theme.css", "./src/css/dikciz.css"] },
      docs: {
        path: ".content",
        routeBasePath: "/",
        sidebarPath: "./sidebars.mjs",
        editUrl: ({ docPath }) => generatedPages.has(docPath) || docPath.startsWith("recipe-")
          ? undefined
          : repository + "/edit/main/docs/" + docPath,
      },
    }],
  ],
  themes: [
    "@docusaurus/theme-mermaid",
    ["@easyops-cn/docusaurus-search-local", {
      docsDir: ".content",
      docsRouteBasePath: "/",
      indexBlog: false,
      hashed: true,
    }],
  ],
  themeConfig: {
    navbar: {
      title: "Dikciz Launcher",
      items: [
        { href: "https://psyb0t.github.io/projects/", label: "All projects", position: "left" },
        { href: repository + "/releases/latest", label: "Download APK", position: "right" },
        { href: repository, label: "GitHub", position: "right" },
        { to: "/changelog/", label: "Changelog", position: "right" },
      ],
    },
    colorMode: { defaultMode: "dark" },
    mermaid: { theme: { light: "neutral", dark: "dark" } },
  },
};
