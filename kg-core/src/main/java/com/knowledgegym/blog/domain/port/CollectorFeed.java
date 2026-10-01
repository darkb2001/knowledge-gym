package com.knowledgegym.blog.domain.port;

import com.knowledgegym.blog.domain.model.CollectedItem;
import com.knowledgegym.blog.domain.model.CollectorSource;
import java.util.List;

public interface CollectorFeed {
    List<CollectedItem> fetch(CollectorSource source) throws Exception;
}
