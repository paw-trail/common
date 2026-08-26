package com.pawtrail.common.message;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface DomainEvent {

    /*EventEnvelope에서 getTopic, getAggregateType, getAggregateId를 뽑아서 담기에
    * 여기서는 JsonIgnore로 무시해야 나중에 중복해서 실리는 것을 막을 수 있다*/

    // 이벤트가 발행될 Kafka 토픽 이름
    @JsonIgnore
    String getTopic();

    // 집합체 타입 (Place, Policy, User...)
    @JsonIgnore
    String getAggregateType();

    // 집합체 식별자 (Kafka의 Partition Key, 동일 집합체에서는 이벤트 순서를 보장)
    @JsonIgnore
    String getAggregateId();
}
