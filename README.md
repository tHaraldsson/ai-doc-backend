AI Document Backend

This repository contains the backend service for an AI-powered document question-answering application.
The backend allows authenticated users to upload documents, extract and process text, store it asynchronously, and ask questions that are answered using an AI model (OpenAI).

The project is built as part of a degree thesis and focuses on scalability, security, and modern reactive architecture.

 Features

User authentication with JWT (HttpOnly cookies)

Secure file upload (PDF, Excel, PowerPoint)

Text extraction and preprocessing

Document chunking for large texts

AI-powered question answering

Embedding-based semantic search for better answers

Reactive and fully asynchronous backend

Per-user document isolation

Connection pooling and circuit breaker for stability

Dockerized and production-ready

 Tech Stack

Java 17

Spring Boot (WebFlux)

Spring Security (Reactive)

R2DBC (Reactive PostgreSQL)

PostgreSQL (Supabase + PgBouncer)

Flyway (database migrations)

OpenAI API

Docker

Gradle

 Architecture Overview

Reactive stack using Spring WebFlux and Project Reactor (Mono / Flux)

Stateless authentication using JWT stored in HttpOnly cookies

Asynchronous database access with R2DBC

Chunk-based document processing for efficient AI usage

Embedding comparison to select the most relevant document chunks

Circuit breaker to protect the system from overloads or external API failures

 Authentication & Security

User registration and login

JWT tokens stored as HttpOnly, Secure cookies

Stateless authentication (no DB lookup per request)

Password hashing using BCrypt

Role-ready JWT claims

CORS configuration for frontend integration

 API Overview
Authentication
POST   /api/auth/register
POST   /api/auth/login
POST   /api/auth/logout
GET    /api/auth/user

Documents
POST   /api/upload
GET    /api/documents
DELETE /api/deletedocument/{id}
GET    /api/textindb

AI
POST   /api/ask
GET    /api/ask-direct


All protected endpoints require authentication.

 Document Processing Flow

User uploads a document

File is validated

Text is extracted

Text is split into chunks (~1000 characters)

Each chunk is embedded using the AI model

Embeddings are stored in the database

User question is embedded

Closest matching chunks are selected

Selected chunks + question are sent to the AI

AI response is returned to the user

This approach:

Improves answer relevance

Reduces token usage

Increases performance

 Database

PostgreSQL hosted on Supabase

Connection pooling via PgBouncer

Reactive access via R2DBC

Schema managed with Flyway

Migration files can be found in:

src/main/resources/db/migration

 Environment Variables

Create a .env file or configure environment variables in your hosting platform:

OPENAI_API_TOKEN=your_openai_api_key

SUPABASE_DB_URL=r2dbc:postgresql://...
SUPABASE_DB_USERNAME=...
SUPABASE_DB_PASSWORD=...

SUPABASE_DB_URL_FLYWAY=jdbc:postgresql://...

JWT_SECRET=your_jwt_secret_at_least_32_chars




 Docker

The backend can be built and run using Docker.

docker build -t ai-doc-backend .
docker run -p 8080:8080 ai-doc-backend

 Error Handling & Stability

Global exception handling

Custom business exceptions

Circuit breaker to stop cascading failures

Graceful handling of external API downtime

Proper logging (no sensitive data)

 Production Considerations

Uses connection pooling to prevent database exhaustion

Asynchronous, non-blocking architecture

Designed to scale horizontally

Secure cookie-based authentication

Ready for cloud deployment (Render / similar platforms)

 Project Context

This backend was developed as part of a degree thesis project with a focus on:

Modern backend architecture

Reactive programming

AI integration

Security best practices

Real-world production challenges

 Author

Tommy Haraldsson
Java Developer (Student)
Degree Thesis Project
