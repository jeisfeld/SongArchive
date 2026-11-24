FROM mariadb:11.8 AS database

COPY --chmod=644 tests/web/fixture.sql /docker-entrypoint-initdb.d/01-fixture.sql

FROM php:8.4-apache AS site

RUN docker-php-ext-install mysqli \
    && htpasswd -bc /etc/apache2/songarchive-test-users fixture fixture-password

COPY tests/web/archive.conf /etc/apache2/conf-enabled/songarchive-test.conf
COPY web/ /var/www/html/
COPY tests/web/db_config.php /var/www/html/db_config.php

RUN mkdir -p /var/www/html/img/songs \
    && cp /var/www/html/img/heilsame_lieder_grafik2.jpg /var/www/html/img/songs/river.jpg \
    && php -r '(new PharData("/var/www/html/chords.zip"))->addFile("/var/www/html/img/songs/river.jpg", "river.jpg");' \
    && chmod -R a+rX /var/www/html \
    && chmod a+r /etc/apache2/conf-enabled/songarchive-test.conf
