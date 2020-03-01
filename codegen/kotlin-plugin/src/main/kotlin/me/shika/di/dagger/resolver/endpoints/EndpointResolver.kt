package me.shika.di.dagger.resolver.endpoints

import me.shika.di.model.Endpoint

typealias EndpointResolver = () -> List<Endpoint>
