package com.knowledgegym.agent;

import com.knowledgegym.blog.application.CollectItemsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.blog.collector.enabled", havingValue="true")
public class CollectorScheduler {
    private static final Logger log=LoggerFactory.getLogger(CollectorScheduler.class);
    private final CollectItemsUseCase collect;
    public CollectorScheduler(CollectItemsUseCase collect){this.collect=collect;}
    @Scheduled(fixedDelayString="${app.blog.collector.poll-ms:600000}", initialDelayString="${app.blog.collector.initial-delay-ms:15000}")
    public void run(){
        try{var result=collect.execute(); if(result.sourcesFetched()>0)log.info("Collector fetched {} sources and inserted {} items",result.sourcesFetched(),result.itemsInserted());}
        catch(RuntimeException e){log.warn("Collector scheduled run failed",e);}
    }
}
