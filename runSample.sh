# Build binary with arthur: mvn clean install arthur:native-image

# Then run it
./target/metrics-scraper.graal.bin \
  -Djava.util.logging.manager="io.yupiik.logging.jul.YupiikLogManager" \
  -Dmetrics-scraper.threading.core="32" -Dmetrics-scraper.threading.max="128" \
  -Dmetrics-scraper.scrapers.length="1" \
  -Dmetrics-scraper.scrapers.0.url="http://localhost:4444" \
  -Dmetrics-scraper.scrapers.0.scrapping.interval="1000" \
  -Dmetrics-scraper.elasticsearch.base="http://localhost:9200" \
  -Dmetrics-scraper.elasticsearch.headers="
                                          Authorization = Basic ZWxhc3RpYzplbGFzdGlj
                                          " \
  -Dmetrics-scraper.elasticsearch.indexPrefix="test-scraper-" \
  -Dmetrics-scraper.elasticsearch.indexNameSuffix="yyyy-MM-dd" \
  -Dio.yupiik.metrics.scraper.level="INFO"