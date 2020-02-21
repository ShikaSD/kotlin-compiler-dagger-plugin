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
import dagger.BindsInstance
import dagger.Component
import dagger.Provides
import dagger.Subcomponent
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import javax.inject.Inject

/**
 * Tests for [subcomponent factories][Subcomponent.Factory].
 *
 *
 * Most things are tested in `FactoryTest`; this is just intended to test some things like
 * injecting subcomponent factories and returning them from component methods.
 */
@RunWith(JUnit4::class)
class SubcomponentFactoryTest {
    @Component
    internal interface ParentWithSubcomponentFactory {
        fun subcomponentFactory(): Sub.Factory
        @Component.Factory
        interface Factory {
            fun create(@BindsInstance i: Int): ParentWithSubcomponentFactory?
        }
    }

    @Subcomponent
    internal interface Sub {
        fun i(): Int
        fun s(): String?
        @Subcomponent.Factory
        interface Factory {
            fun create(@BindsInstance s: String?): Sub
        }
    }

    @Test
    fun parentComponentWithSubcomponentFactoryEntryPoint() {
        val parent: ParentWithSubcomponentFactory =
            DaggerSubcomponentFactoryTest_ParentWithSubcomponentFactory.factory().create(3)
        val subcomponent = parent.subcomponentFactory().create("foo")
        assertThat(subcomponent.i()).isEqualTo(3)
        assertThat(subcomponent.s()).isEqualTo("foo")
    }

    internal object ModuleWithSubcomponent {
        @Provides
        fun provideInt(): Int {
            return 42
        }
    }

    internal class UsesSubcomponentFactory @Inject constructor(private val subFactory: Sub.Factory) {
        fun getSubcomponent(s: String?): Sub {
            return subFactory.create(s)
        }

    }

    @Component(modules = [ModuleWithSubcomponent::class])
    internal interface ParentWithModuleWithSubcomponent {
        fun usesSubcomponentFactory(): UsesSubcomponentFactory
    }

    @Test
    fun parentComponentWithModuleWithSubcomponent() {
        val parent: ParentWithModuleWithSubcomponent =
            DaggerSubcomponentFactoryTest_ParentWithModuleWithSubcomponent.create()
        val usesSubcomponentFactory =
            parent.usesSubcomponentFactory()
        val subcomponent1 =
            usesSubcomponentFactory.getSubcomponent("foo")
        assertThat(subcomponent1.i()).isEqualTo(42)
        assertThat(subcomponent1.s()).isEqualTo("foo")
        val subcomponent2 =
            usesSubcomponentFactory.getSubcomponent("bar")
        assertThat(subcomponent2.i()).isEqualTo(42)
        assertThat(subcomponent2.s()).isEqualTo("bar")
    }
}
