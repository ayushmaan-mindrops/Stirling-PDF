# PaperBolt

**Professional PDF tools for your workflow**

PaperBolt is a streamlined PDF management platform focused on the essential tools you need most. Process documents locally with privacy-first architecture - no external services, no data sharing.

## Core Features

PaperBolt v1 includes 7 essential PDF tools:

1. **Merge** - Combine multiple PDFs into a single document
2. **Add Password** - Protect PDFs with password encryption
3. **Rotate** - Rotate pages in your PDFs
4. **Split** - Split PDFs into multiple documents
5. **Add Page Numbers** - Add page numbers to your documents
6. **Remove Password** - Unlock password-protected PDFs
7. **Compress** - Reduce PDF file sizes

## Quick Start with Docker

```bash
docker-compose -f docker/compose/docker-compose.yml up
```

Then open: http://localhost:8080

See [DEPLOYMENT.md](DEPLOYMENT.md) for detailed deployment instructions.

## Development

For development setup and guidelines, see:
- [Developer Guide](DeveloperGuide.md)
- [AGENTS.md](AGENTS.md) - AI agent development guidance

## Architecture

- **Frontend**: React + TypeScript + Vite + Mantine UI
- **Backend**: Spring Boot (Java)
- **PDF Processing**: PDFBox, LibreOffice, Tesseract OCR
- **Deployment**: Docker, Docker Compose

## License

See [LICENSE](LICENSE) for details.
