package me.shika.di.dagger.resolver.bindings

import me.shika.di.model.Binding

typealias BindingResolver = () -> List<Binding>
