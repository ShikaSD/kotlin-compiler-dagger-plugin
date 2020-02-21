/*
 * Copyright (C) 2019 The Dagger Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.shika.di.factory

import com.google.common.truth.Truth.assertThat
import dagger.Component
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for factories for components with a dependency.  */
@RunWith(JUnit4::class)
class FactoryDependenciesTest {
    @Component(dependencies = [Dependency::class])
    internal interface DependencyComponent {
        fun `object`(): Any?
        @Component.Factory
        interface Factory {
            fun create(dependency: Dependency?): DependencyComponent?
        }
    }

    @Test
    fun dependency() {
        val component: DependencyComponent =
            DaggerFactoryDependenciesTest_DependencyComponent.factory().create(Dependency())
        assertThat(component.`object`()).isEqualTo("bar")
    }

    @Test
    fun dependency_failsOnNull() {
        try {
            DaggerFactoryDependenciesTest_DependencyComponent.factory().create(null)
            fail()
        } catch (expected: NullPointerException) {
        }
    }
}
