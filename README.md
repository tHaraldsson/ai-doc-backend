# AI Document Backend

This repository contains the backend service for an AI-powered document question-answering application.  
The backend allows authenticated users to upload documents, extract and process text, store it asynchronously, and ask questions that are answered using an AI model (OpenAI).

This project was developed as part of a degree thesis, focusing on scalability, security, and a reactive architecture.

---

## Features

- User authentication with JWT (HttpOnly cookies)
- Secure file upload (PDF, Excel, PowerPoint)
- Text extraction and preprocessing
- Document chunking (~800 chars, 150 overlap)
- AI-powered question answering via GPT-3.5 Turbo
- Embedding generation using `text-embedding-ada-002`
- In-memory cosine similarity search for relevant chunk selection
- Keyword-based fallback search if embedding fails
- Retry logic with exponential backoff on embedding timeouts
- Reactive and fully asynchronous backend
- Per-user document isolation
- Connection pooling and circuit breaker for stability
- Dockerized and production-ready

---

## Tech Stack

- Java 21
- Spring Boot (WebFlux)
- Spring Security (Reactive)
- R2DBC (Reactive PostgreSQL)
- PostgreSQL (Supabase + PgBouncer)
- Flyway (database migrations)
- OpenAI API (GPT-3.5 Turbo for question answering, text-embedding-ada-002 for semantic embeddings)
- Docker
- Gradle

---

## Architecture Overview

- Reactive stack using Spring WebFlux and Project Reactor (`Mono` / `Flux`)
- Stateless authentication using JWT stored in HttpOnly cookies
- Asynchronous database access with R2DBC
- Chunk-based document processing (~800 characters, 150 overlap) for efficient AI usage
- In-memory cosine similarity to select the top 5 most relevant document chunks
- Keyword-based fallback search if embedding generation fails
- Circuit breaker to protect the system from overloads or external API failures

---

## Authentication and Security

- User registration and login
- JWT tokens stored as HttpOnly, Secure cookies
- Stateless authentication (no database lookup per request)
- Password hashing using BCrypt
- Role-ready JWT claims
- CORS configuration for frontend integration

---

## API Overview

### Authentication
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout
GET /api/auth/user


### Documents
POST /api/upload
GET /api/documents
DELETE /api/deletedocument/{id}
GET /api/textindb


### AI
POST /api/ask
GET /api/ask-direct


_All protected endpoints require authentication._

---

## Document Processing Flow

1. User uploads a document
2. File is validated
3. Text is extracted (PDF, Excel, PowerPoint supported)
4. Text is split into chunks (~800 characters, 150 overlap)
5. Each chunk is embedded using `text-embedding-ada-002`
6. Embeddings are stored as JSON in PostgreSQL (`embedding_json` column)
7. User question is embedded
8. Cosine similarity is computed in-memory between question and all chunks
9. Top 5 most relevant chunks are selected
10. Selected chunks + question are sent to GPT-3.5 Turbo
11. AI response is returned to the user

> Falls back to keyword search if embedding generation fails.

This improves answer relevance, reduces token usage, and increases performance.

---

## Database

- PostgreSQL hosted on Supabase
- Connection pooling via PgBouncer
- Reactive access via R2DBC
- Schema managed with Flyway

Migration files are located in:  
`src/main/resources/db/migration`

---

## Environment Variables

Create a `.env` file or configure environment variables in your hosting platform:

OPENAI_API_TOKEN=your_openai_api_key

SUPABASE_DB_URL=r2dbc:postgresql://...
SUPABASE_DB_USERNAME=...
SUPABASE_DB_PASSWORD=...

SUPABASE_DB_URL_FLYWAY=jdbc:postgresql://...

JWT_SECRET=your_jwt_secret_at_least_32_chars


---

## Docker

Build and run the backend using Docker:

docker build -t ai-doc-backend .
docker run -p 8080:8080 ai-doc-backend


---

## Error Handling and Stability

- Global exception handling
- Custom business exceptions
- Circuit breaker to prevent cascading failures
- Retry logic with exponential backoff on embedding timeouts (2 retries, 2s base delay)
- Graceful handling of external API downtime
- Proper logging without exposing sensitive data

---

## Production Considerations

- Connection pooling to prevent database exhaustion
- Asynchronous, non-blocking architecture
- Designed for horizontal scaling
- Secure cookie-based authentication
- Ready for cloud deployment (Render or similar platforms)

---

## Frontend

A companion frontend is available and integrated with this backend:  
[https://ai-doc-frontend-ouqn.onrender.com](https://ai-doc-frontend-ouqn.onrender.com)

CORS is configured to allow requests from this origin.

---

## Author

Tommy Haraldsson  
Java Developer (Student)  
Degree Thesis Project
