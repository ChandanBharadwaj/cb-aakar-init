# admin: Next.js app built inside the pnpm workspace. Build context is the repo root:
#   docker build -f apps/admin/Dockerfile -t aakar-admin .
FROM node:22-alpine AS build
RUN corepack enable && corepack prepare pnpm@10.33.0 --activate
WORKDIR /repo
COPY pnpm-workspace.yaml package.json pnpm-lock.yaml ./
COPY packages ./packages
COPY apps/admin ./apps/admin
RUN pnpm install --frozen-lockfile --filter @aakar/admin...
ARG NEXT_PUBLIC_API_URL=http://localhost:8080
ARG NEXT_PUBLIC_AAKAR_PROFILE=local
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL NEXT_PUBLIC_AAKAR_PROFILE=$NEXT_PUBLIC_AAKAR_PROFILE
RUN pnpm --filter @aakar/admin build

FROM node:22-alpine AS run
RUN corepack enable && corepack prepare pnpm@10.33.0 --activate
WORKDIR /repo
COPY --from=build /repo ./
ENV NODE_ENV=production PORT=3100
EXPOSE 3100
USER node
CMD ["pnpm", "--filter", "@aakar/admin", "start"]
