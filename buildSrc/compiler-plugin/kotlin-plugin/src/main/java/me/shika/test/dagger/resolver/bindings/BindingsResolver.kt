package me.shika.test.dagger.resolver.bindings

import me.shika.test.model.Binding

typealias BindingResolver = () -> List<Binding>
