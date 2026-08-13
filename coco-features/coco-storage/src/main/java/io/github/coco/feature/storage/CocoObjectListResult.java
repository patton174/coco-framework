package io.github.coco.feature.storage;

import java.util.List;
/** 一页已按键排序的对象状态及下一页令牌。 */
public record CocoObjectListResult(List<CocoObjectStat> objects, String continuationToken) {
    public CocoObjectListResult { objects = List.copyOf(objects); }
}
