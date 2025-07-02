# Changelog

## [1.0.3] - 2024-12-19

### 🚀 Major Features
- **Module-based Translation** - Auto-detect Android modules, no need to manually select input/output files
- **KMM Support** - Full Kotlin Multiplatform Mobile support (shared, androidMain, commonMain)
- **Smart Project Scanner** - Automatically find all translatable modules in project

### 🔧 Improvements  
- **Refactored Services** - Split 865-line TranslationService into 4 specialized services
- **Dynamic Timeout** - Smart timeout scaling (30s + 5s per string, max 300s)
- **Better Error Handling** - Fixed silent fallback bugs, proper exception propagation
- **Enhanced UI** - Module dropdown, type indicators, project info dialog

### 🐛 Bug Fixes
- Fixed translation failures writing original text instead of throwing errors
- Resolved timeout issues for large translation batches
- Improved dependency injection with proper service container

### 📱 Supported Project Types
- Traditional Android projects
- KMM projects (shared/androidMain/commonMain)
- Multi-module Android projects
- Android library modules

## [1.0.2] - 2024-12-18
- Enhanced string filtering
- Improved translation reliability

## [1.0.1] - 2024-12-17
- Initial release with basic translation features 