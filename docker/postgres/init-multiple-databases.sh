#!/usr/bin/env bash

main() {
  local database
  local -a databases

  if [[ -z "${POSTGRES_MULTIPLE_DATABASES:-}" ]]; then
    echo "POSTGRES_MULTIPLE_DATABASES is empty; no additional databases to create."
    return 0
  fi

  IFS=',' read -ra databases <<< "${POSTGRES_MULTIPLE_DATABASES}"

  for database in "${databases[@]}"; do
    database="$(echo "${database}" | xargs)"

    if [[ -z "${database}" ]]; then
      continue
    fi

    echo "Ensuring PostgreSQL database '${database}' exists..."
    psql \
      --username "${POSTGRES_USER}" \
      --dbname "${POSTGRES_DB:-${POSTGRES_USER}}" \
      --set ON_ERROR_STOP=1 \
      --set database_name="${database}" <<-'EOSQL' || return 1
SELECT format('CREATE DATABASE %I', :'database_name')
WHERE NOT EXISTS (
  SELECT 1
  FROM pg_database
  WHERE datname = :'database_name'
) \gexec
EOSQL
  done

  echo "Additional PostgreSQL databases are ready."
}

main "$@"
