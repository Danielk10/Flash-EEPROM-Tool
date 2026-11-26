#!/usr/bin/env bash
# Descarga los artefactos de InMobi usados por la mediación de AdMob.
#
# No hace falta copiarlos a app/libs: Gradle los resuelve desde Maven.
# Este script los deja en .inmobi-sdk/ para poder inspeccionarlos sin abrir
# el caché de Gradle, y verifica que las descargas estén íntegras.
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT_DIR="${PROJECT_ROOT}/.inmobi-sdk"

ADAPTER_VERSION="11.5.1.0"
SDK_VERSION="11.5.1"

GOOGLE_MAVEN="https://dl.google.com/dl/android/maven2"
MAVEN_CENTRAL="https://repo1.maven.org/maven2"

mkdir -p "${OUT_DIR}"

fetch() {
  local url="$1"
  local dest="$2"

  if [[ -s "${dest}" ]]; then
    echo "ya existe: $(basename "${dest}")"
    return 0
  fi

  echo "descargando: $(basename "${dest}")"
  curl -fsSL --retry 3 --retry-delay 2 --connect-timeout 20 \
    "${url}" -o "${dest}.part"
  mv "${dest}.part" "${dest}"
}

check_aar() {
  local file="$1"

  if [[ ! -s "${file}" ]]; then
    echo "ERROR: ${file} está vacío" >&2
    return 1
  fi

  if ! unzip -tq "${file}" >/dev/null 2>&1; then
    echo "ERROR: ${file} no es un AAR válido" >&2
    return 1
  fi

  local listing
  listing="$(unzip -l "${file}")"

  if ! grep -q 'classes.jar' <<<"${listing}"; then
    echo "ERROR: ${file} no contiene classes.jar" >&2
    return 1
  fi

  local size
  size="$(du -h "${file}" | cut -f1)"
  echo "OK  $(basename "${file}")  ${size}"
}

echo "== Adapter de mediación (Google Maven) =="
fetch "${GOOGLE_MAVEN}/com/google/ads/mediation/inmobi/${ADAPTER_VERSION}/inmobi-${ADAPTER_VERSION}.aar" \
      "${OUT_DIR}/inmobi-adapter-${ADAPTER_VERSION}.aar"
fetch "${GOOGLE_MAVEN}/com/google/ads/mediation/inmobi/${ADAPTER_VERSION}/inmobi-${ADAPTER_VERSION}.pom" \
      "${OUT_DIR}/inmobi-adapter-${ADAPTER_VERSION}.pom"

echo
echo "== SDK de InMobi (Maven Central) =="
fetch "${MAVEN_CENTRAL}/com/inmobi/monetization/inmobi-ads-kotlin/${SDK_VERSION}/inmobi-ads-kotlin-${SDK_VERSION}.aar" \
      "${OUT_DIR}/inmobi-ads-kotlin-${SDK_VERSION}.aar"
fetch "${MAVEN_CENTRAL}/com/inmobi/monetization/inmobi-ads-kotlin/${SDK_VERSION}/inmobi-ads-kotlin-${SDK_VERSION}.pom" \
      "${OUT_DIR}/inmobi-ads-kotlin-${SDK_VERSION}.pom"

echo
echo "== Verificación =="
check_aar "${OUT_DIR}/inmobi-adapter-${ADAPTER_VERSION}.aar"
check_aar "${OUT_DIR}/inmobi-ads-kotlin-${SDK_VERSION}.aar"

echo
echo "== SHA-256 =="
sha256sum "${OUT_DIR}"/*.aar | sed "s#${OUT_DIR}/##"

echo
echo "Artefactos en ${OUT_DIR}"
echo "Gradle los resolverá igualmente desde Maven al compilar."
