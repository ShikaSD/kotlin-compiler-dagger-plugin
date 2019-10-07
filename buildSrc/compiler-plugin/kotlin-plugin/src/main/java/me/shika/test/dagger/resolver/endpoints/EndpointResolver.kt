package me.shika.test.dagger.resolver.endpoints

import me.shika.test.model.Endpoint

typealias EndpointResolver = () -> List<Endpoint>
