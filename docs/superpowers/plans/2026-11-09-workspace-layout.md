# Workspace layout alignment

Use the dashboard geometry for workspace pages: 96% width, maximum 1800px, 24px top spacing and column gap, 224px desktop navigation, and a mobile menu below 768px.

Implemented with the existing Semi Layout and a shared ProfileShell. Removed the training-specific shell and duplicated page containers. Overview, dashboard, training pages, banks, categories, questions, papers, compose, assessments, scores, statistics, organization and settings now use the shared container. The employee workspace reuses the same shell; standalone training and answering flows remain separate.

Header aligns with workspace edges. Management headings use the dashboard green. Filter/card spacing is aligned; training filters use a desktop row and mobile stack. Training and dashboard table action columns only stick on desktop; training pagination hides verbose totals and size selection on mobile.

Validation: TypeScript passed; ESLint reports zero errors and seven existing warnings in management pages (effect dependencies and QR images). Eighteen list/detail/editor routes checked at 1700px and 390px with authenticated local data: no document overflow or page exceptions. These are layout checks, not a new destructive end-to-end test of every business action.
