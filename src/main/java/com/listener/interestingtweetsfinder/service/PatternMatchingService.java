package com.listener.interestingtweetsfinder.service;

import com.listener.interestingtweetsfinder.PatternUpdateRunnable;
import com.listener.interestingtweetsfinder.model.Regex;
import com.listener.interestingtweetsfinder.repository.RegexRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Service
public class PatternMatchingService {

    private static final Logger logger = LoggerFactory.getLogger (PatternMatchingService.class);

    private final ScheduledExecutorService scheduledExecutorService;
    private final RegexRepository regexRepository;
    private final Map<String, Pattern> regexPatternMap;

    public PatternMatchingService(RegexRepository regexRepository){
        this.regexRepository=regexRepository;
        regexPatternMap=new ConcurrentHashMap<> ();
        for(Regex regex : regexRepository.findAll ()){
            if(regex.getCaseSensitivity ())
                regexPatternMap.put (regex.getId (),Pattern.compile (regex.getExpression ()));
            else
                regexPatternMap.put (regex.getId (),Pattern.compile (regex.getExpression (),Pattern.CASE_INSENSITIVE));
        }
        scheduledExecutorService= Executors.newSingleThreadScheduledExecutor ();
        startUpdateScheduler ();
        logger.info ("Initially found "+regexPatternMap.size ()+" patterns! ");
    }

    private void startUpdateScheduler(){
        scheduledExecutorService.scheduleWithFixedDelay (new PatternUpdateRunnable (regexPatternMap,regexRepository),
                5,
                5,
                TimeUnit.SECONDS
        );
    }

    public List<String> findMatchingRegexIdsForText(String text){
        List<String> matches = new ArrayList<> ();
        for(Map.Entry<String,Pattern> entry: regexPatternMap.entrySet ()){
            if(entry.getValue ().matcher (text).matches ()){
                matches.add(entry.getKey ());
            }
        }
        return matches;
    }

    public boolean isInteresting(String text){
        for(Map.Entry<String,Pattern> entry: regexPatternMap.entrySet ()){
            if(entry.getValue ().matcher (text).matches ())
                return true;
        }
        return false;
    }

    public Set<String> getInterestingReasonIds(){
        return regexPatternMap.keySet ();
    }

    @PreDestroy
    public void stop(){
        logger.info ("Shutting down executor service");
        scheduledExecutorService.shutdown ();
        try {
            scheduledExecutorService.awaitTermination (2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.warn ("Not able to wait for termination. Forcefully shutting down service!");
            e.printStackTrace ();
        }
        scheduledExecutorService.shutdownNow ();
    }
}