FROM mariadb:11.8 AS database

COPY --chmod=644 tests/web/fixture.sql /docker-entrypoint-initdb.d/01-fixture.sql

FROM python:3.13-slim-trixie AS importer-runtime

FROM php:8.4-apache AS site

RUN docker-php-ext-install mysqli \
    && htpasswd -bc /etc/apache2/songarchive-test-users fixture fixture-password

COPY --from=importer-runtime /usr/local/bin/python3* /usr/local/bin/
COPY --from=importer-runtime /usr/local/lib/libpython3*.so* /usr/local/lib/
COPY --from=importer-runtime /usr/local/lib/python3.13/ /usr/local/lib/python3.13/

COPY tests/web/archive.conf /etc/apache2/conf-enabled/songarchive-test.conf
COPY web/ /var/www/html/
COPY tests/web/db_config.php /var/www/html/db_config.php
COPY --chmod=644 tools/import_chants.py tools/chant-variants.json /var/www/tools/
COPY tests/web/chants-document.xml /tmp/chants-fixture/word/document.xml

RUN mkdir -p /var/www/html/img/songs \
    && cp /var/www/html/img/heilsame_lieder_grafik2.jpg /var/www/html/img/songs/river.jpg \
    && cd /tmp/chants-fixture && python3 -m zipfile -c /var/www/html/Chants.docx word \
    && php -r '(new PharData("/var/www/html/chords.zip"))->addFile("/var/www/html/img/songs/river.jpg", "river.jpg");' \
    && chmod -R a+rX /var/www/html \
    && chmod 755 /var/www/tools \
    && chmod a+r /etc/apache2/conf-enabled/songarchive-test.conf
