package zenuo.gogo.core.processor.impl;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import zenuo.gogo.core.ResponseType;
import zenuo.gogo.core.processor.IProcessor;
import zenuo.gogo.core.processor.ISearchResultProvider;
import zenuo.gogo.exception.SearchException;
import zenuo.gogo.model.SearchResponse;
import zenuo.gogo.service.ICacheService;
import zenuo.gogo.util.JsonUtils;
import zenuo.gogo.web.IPageBuilder;

import javax.annotation.PostConstruct;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component("searchProcessor")
@RequiredArgsConstructor
final class SearchProcessorImpl implements IProcessor {

    @NonNull
    private final IPageBuilder resultPageBuilder;

    @NonNull
    private final ICacheService cacheService;

    @NonNull
    private final List<ISearchResultProvider> searchResultProviders;

    @PostConstruct
    public void postConstruct() {
        //打印日志，搜索结果提供者列表
        log.info("searchResultProviders={}", searchResultProviders);
    }

    @Override
    public void process(ChannelHandlerContext ctx, FullHttpRequest request, QueryStringDecoder decoder, ResponseType responseType) {
        final List<String> keys = decoder.parameters().get("q");
        if (keys == null || "".equals(keys.get(0))) {
            response(ctx,
                    request,
                    ResponseType.API,
                    "{\"error\": \"the keyword should not be empty\"}",
                    HttpResponseStatus.BAD_REQUEST);
        } else {
            final List<String> pages = decoder.parameters().get("p");
            //根据优先级调用提供者
            final String key = keys.get(0);
            final int page = pages == null || "".equals(pages.get(0)) ? 1 : Integer.parseInt(pages.get(0));

            SearchResponse response = null;
            SearchException searchException = null;

            if (page < 1) {
                response = SearchResponse.builder().error("page must be greater than zero!")
                        .status(HttpResponseStatus.BAD_REQUEST)
                        .build();
            } else {
                //尝试读取缓存
                final Optional<SearchResponse> optional = readCache(key, page);
                if (optional.isPresent()) {
                    //若缓存存在
                    response = optional.get();
                } else {
                    //若不存在
                    for (ISearchResultProvider srp : searchResultProviders) {
                        try {
                            response = srp.search(key, page);
                            //若不抛出异常，则退出循环
                            break;
                        } catch (SearchException e) {
                            //忽略
                            searchException = e;
                        }
                    }
                    //若结果不为null，则写入缓存
                    if (response != null) {
                        writeCache(key, page, response);
                    }
                }
            }

            if (response == null) {
                response(ctx,
                        request,
                        responseType,
                        responseType == ResponseType.API ? "{\"error\": \"" + searchException.getMessage() + "\"}"
                                : resultPageBuilder.build(SearchResponse.builder().key(key).error(searchException.getMessage()).build()),
                        HttpResponseStatus.OK);

            } else {
                response(ctx,
                        request,
                        responseType,
                        responseType == ResponseType.API ? JsonUtils.toJson(response) : resultPageBuilder.build(response),
                        response.getStatus() == null ? HttpResponseStatus.OK : response.getStatus());
            }
        }
    }

    private Optional<SearchResponse> readCache(String key, int page) {
        return searchResultProviders.get(0).readCache(cacheService, key, page);
    }

    private void writeCache(String key, int page, SearchResponse searchResponse) {
        searchResultProviders.get(0).writeCache(cacheService, key, page, searchResponse);
    }
}
