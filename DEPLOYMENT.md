# PaperBolt Deployment Guide

This guide covers deploying PaperBolt using Docker and Docker Compose.

## Prerequisites

- Docker Engine 20.10+
- Docker Compose 2.0+
- 2GB RAM minimum (4GB recommended)
- 10GB disk space

## Quick Start (Docker Compose)

The simplest way to run PaperBolt:

```bash
# Clone the repository
git clone <repository-url>
cd paperbolt

# Start the application
docker-compose -f docker/compose/docker-compose.yml up -d

# View logs
docker-compose -f docker/compose/docker-compose.yml logs -f

# Stop the application
docker-compose -f docker/compose/docker-compose.yml down
```

Access PaperBolt at: http://localhost:8080

## Configuration

### Environment Variables

Configure PaperBolt by setting environment variables in the docker-compose.yml file:

```yaml
environment:
  # Application Settings
  UI_APPNAME: "PaperBolt"
  UI_HOMEDESCRIPTION: "Professional PDF tools for your workflow"
  UI_APPNAMENAVBAR: "PaperBolt"
  
  # Security
  SECURITY_ENABLELOGIN: "false"  # Set to "true" to enable authentication
  
  # System Settings
  SYSTEM_DEFAULTLOCALE: "en-US"
  SYSTEM_MAXFILESIZE: "100"  # Maximum file size in MB
  
  # Features
  DISABLE_ADDITIONAL_FEATURES: "true"  # Disable non-core features
  METRICS_ENABLED: "true"
  SYSTEM_GOOGLEVISIBILITY: "false"
  SHOW_SURVEY: "false"
```

### Persistent Data

PaperBolt stores data in the following volumes:

- `paperbolt-data`: OCR language data
- `paperbolt-config`: Application configuration
- `paperbolt-logs`: Application logs

These are automatically created by Docker Compose.

### Custom Configuration File

For advanced configuration, create a `settings.yml` file:

```yaml
# settings.yml
system:
  defaultLocale: 'en-US'
  maxFileSize: 100

ui:
  appName: 'PaperBolt'
  homeDescription: 'Professional PDF tools for your workflow'
  appNameNavbar: 'PaperBolt'

security:
  enableLogin: false
```

Mount it in docker-compose.yml:

```yaml
volumes:
  - ./settings.yml:/configs/settings.yml:ro
```

## Production Deployment

### Using Docker Compose (Recommended)

1. **Create a production docker-compose.yml**:

```yaml
services:
  paperbolt:
    image: paperbolt:latest
    container_name: paperbolt-prod
    restart: always
    ports:
      - "8080:8080"
    volumes:
      - paperbolt-data:/usr/share/tessdata
      - paperbolt-config:/configs
      - paperbolt-logs:/logs
    environment:
      SECURITY_ENABLELOGIN: "true"
      SYSTEM_MAXFILESIZE: "200"
      METRICS_ENABLED: "true"
    networks:
      - paperbolt-network
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/api/v1/info/status | grep -q 'UP'"]
      interval: 30s
      timeout: 10s
      retries: 3

networks:
  paperbolt-network:
    driver: bridge

volumes:
  paperbolt-data:
  paperbolt-config:
  paperbolt-logs:
```

2. **Start in production mode**:

```bash
docker-compose -f docker-compose.prod.yml up -d
```

### Behind a Reverse Proxy (Nginx)

Example Nginx configuration:

```nginx
server {
    listen 80;
    server_name paperbolt.example.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Increase timeouts for large file uploads
        proxy_read_timeout 300;
        proxy_connect_timeout 300;
        proxy_send_timeout 300;
        
        # Increase max body size for file uploads
        client_max_body_size 100M;
    }
}
```

### SSL/TLS Configuration

For HTTPS, use Let's Encrypt with Certbot:

```bash
# Install Certbot
sudo apt-get install certbot python3-certbot-nginx

# Obtain certificate
sudo certbot --nginx -d paperbolt.example.com

# Auto-renewal is configured automatically
```

## Building from Source

To build PaperBolt from source:

```bash
# Build the Docker image
docker build -f docker/embedded/Dockerfile -t paperbolt:latest .

# Run the container
docker run -d -p 8080:8080 --name paperbolt paperbolt:latest
```

## Troubleshooting

### Container won't start

Check logs:
```bash
docker-compose logs paperbolt
```

### Port already in use

Change the port mapping in docker-compose.yml:
```yaml
ports:
  - "8081:8080"  # Use port 8081 instead
```

### Out of memory

Increase Docker memory limit or add swap:
```yaml
services:
  paperbolt:
    mem_limit: 2g
    memswap_limit: 4g
```

### Permission issues with volumes

Fix volume permissions:
```bash
sudo chown -R 1000:1000 ./paperbolt
```

## Monitoring

### Health Check

Check application health:
```bash
curl http://localhost:8080/api/v1/info/status
```

Expected response:
```json
{"status":"UP"}
```

### Logs

View application logs:
```bash
# Docker Compose
docker-compose logs -f paperbolt

# Docker
docker logs -f paperbolt
```

## Backup and Restore

### Backup

```bash
# Backup volumes
docker run --rm -v paperbolt-data:/data -v $(pwd):/backup alpine tar czf /backup/paperbolt-data-backup.tar.gz /data
docker run --rm -v paperbolt-config:/data -v $(pwd):/backup alpine tar czf /backup/paperbolt-config-backup.tar.gz /data
```

### Restore

```bash
# Restore volumes
docker run --rm -v paperbolt-data:/data -v $(pwd):/backup alpine tar xzf /backup/paperbolt-data-backup.tar.gz -C /
docker run --rm -v paperbolt-config:/data -v $(pwd):/backup alpine tar xzf /backup/paperbolt-config-backup.tar.gz -C /
```

## Updating

To update PaperBolt to a new version:

```bash
# Pull latest changes
git pull

# Rebuild and restart
docker-compose down
docker-compose build
docker-compose up -d
```

## Support

For issues or questions:
- Check the logs first
- Review this deployment guide
- See [README.md](README.md) for general information
- See [DeveloperGuide.md](DeveloperGuide.md) for development setup
