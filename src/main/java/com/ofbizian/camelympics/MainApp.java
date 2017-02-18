/**
 * The MIT License (MIT)
 *
 *Copyright (c) 2016 Bilgin Ibryam
 *
 *Permission is hereby granted, free of charge, to any person obtaining a copy
 *of this software and associated documentation files (the "Software"), to deal
 *in the Software without restriction, including without limitation the rights
 *to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *copies of the Software, and to permit persons to whom the Software is
 *furnished to do so, subject to the following conditions:
 *
 *The above copyright notice and this permission notice shall be included in all
 *copies or substantial portions of the Software.
 *
 *THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *SOFTWARE.
 */
package com.ofbizian.camelympics;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.main.Main;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.spi.ThreadPoolProfile;
import twitter4j.MediaEntity;
import twitter4j.Status;

public final class MainApp {
    private static long tweetCount;
    private static long imageCount;

    public static void main(String[] args) throws Exception {
        System.out.println("___________________Camelympics___________________");
        System.out.println("Open your web browser on http://localhost:8080");
        System.out.println("Press ctrl+c to stop this application");
        System.out.println("__________________________________________________");
        Main main = new Main();
        main.enableHangupSupport();
        main.addRouteBuilder(new RouteBuilder() {

            @Override
            public void configure() throws Exception {

                PropertiesComponent properties = new PropertiesComponent();
                properties.setLocation("classpath:app.properties");
                properties.setSystemPropertiesMode(PropertiesComponent.SYSTEM_PROPERTIES_MODE_OVERRIDE);
                getContext().addComponent("properties", properties);

                ThreadPoolProfile throttlerPool = new ThreadPoolProfile("throttlerPool");
                throttlerPool.setRejectedPolicy(ThreadPoolRejectedPolicy.Discard);
                throttlerPool.setMaxQueueSize(10);
                throttlerPool.setMaxPoolSize(2);
                throttlerPool.setPoolSize(2);
                getContext().getExecutorServiceManager().registerThreadPoolProfile(throttlerPool);
                getContext().getShutdownStrategy().setTimeout(1);

                from("twitter://streaming/filter?type=event&keywords={{searchTerm}}&accessToken={{accessToken}}&accessTokenSecret={{accessTokenSecret}}&consumerKey={{consumerKey}}&consumerSecret={{consumerSecret}}")

                        .to("log:tweetStream?level=INFO&groupInterval=10000&groupDelay=50000&groupActiveOnly=false")

                        .process(new Processor() {

                            @Override
                            public void process(Exchange exchange) throws Exception {
                                tweetCount++;
                                Status status = exchange.getIn().getBody(Status.class);
                                MediaEntity[] mediaEntities = status.getMediaEntities();
                                if (mediaEntities != null && !status.isPossiblySensitive()) { //nsfw
                                    for (MediaEntity mediaEntity : mediaEntities) {
                                        imageCount++;
                                        exchange.getIn().setBody(
                                                new Tweet()
                                                        .withName(status.getUser().getScreenName())
                                                        .withText(status.getText())
                                                        .withCount(tweetCount)
                                                        .withImageCount(imageCount)
                                                        .withTweetUrl(mediaEntity.getDisplayURL().toString())
                                                        .withImageUrl(mediaEntity.getMediaURLHttps().toString())
                                        );

                                        exchange.getIn().setHeader("UNIQUE_IMAGE_URL", mediaEntity.getMediaURL().toString());
                                        break; //grab only the first image
                                    }
                                }
                            }
                        })

                    .filter(body().isInstanceOf(Tweet.class))

                    .idempotentConsumer(header("UNIQUE_IMAGE_URL"), MemoryIdempotentRepository.memoryIdempotentRepository(10000))

                    .throttle(1).timePeriodMillis(500).asyncDelayed().executorServiceRef("throttlerPool")

                    .marshal().json(JsonLibrary.Jackson).convertBodyTo(String.class)

                    .to("websocket://0.0.0.0:8080/camelympics?sendToAll=true&staticResources=classpath:web/.");
                 }
            });

        main.run();
    }

    static class Tweet {
        private String name;
        private String text;
        private String imageUrl;
        private String tweetUrl;
        private long tweetCount;
        private long imageCount;

        public Tweet withName(String name) {
            this.name = name;
            return this;
        }

        public Tweet withText(String text) {
            this.text = text;
            return this;
        }

        public Tweet withImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
            return this;
        }

        public Tweet withTweetUrl(String tweetUrl) {
            this.tweetUrl = tweetUrl;
            return this;
        }

        public Tweet withCount(long tweetCount) {
            this.tweetCount = tweetCount;
            return this;
        }

        public Tweet withImageCount(long imageCount) {
            this.imageCount = imageCount;
            return this;
        }

        public long getImageCount() {
            return imageCount;
        }

        public String getName() {
            return name;
        }

        public String getText() {
            return text;
        }

        public long getTweetCount() {
            return tweetCount;
        }

        public String getUrl() {
            return imageUrl;
        }

        public String getTweetUrl() {
            return tweetUrl;
        }

        @Override
        public String toString() {
            return "Tweet{" +
                    "name='" + name + '\'' +
                    ", text='" + text + '\'' +
                    ", imageUrl='" + imageUrl + '\'' +
                    ", tweetUrl='" + tweetUrl + '\'' +
                    ", tweetCount=" + tweetCount +
                    ", imageCount=" + imageCount +
                    '}';
        }
    }
}

