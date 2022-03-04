FROM keymetrics/pm2:12-alpine

COPY public ./public
COPY environments ./environments
COPY src ./src
COPY package.json ./package.json
COPY .babelrc ./.babelrc
COPY next.config.js ./next.config.js
COPY yarn.lock ./yarn.lock
ARG COUNTRY_LANG
ARG ENVIRONMENT

ENV COUNTRY_LANG=${COUNTRY_LANG}
ENV environment=${ENVIRONMENT}

# Set Up - Private Registry
COPY .npmrc .npmrc
RUN npm config set registry https://konfioglobal.jfrog.io/artifactory/api/npm/konfiolibrary_virtual_npm/

RUN yarn install --silent
RUN yarn next build

CMD yarn next start -p 80