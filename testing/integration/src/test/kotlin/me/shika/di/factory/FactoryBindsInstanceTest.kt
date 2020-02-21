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
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/** Tests for component factories with [BindsInstance] parameters.  */
@RunWith(JUnit4::class)
class FactoryBindsInstanceTest {
    @Component
    internal interface BindsInstanceComponent {
        fun string(): String?
        @Component.Factory
        interface Factory {
            fun create(@BindsInstance string: String?): BindsInstanceComponent?
        }
    }

    @Test
    fun bindsInstance() {
        val component: BindsInstanceComponent =
            DaggerFactoryBindsInstanceTest_BindsInstanceComponent.factory().create("baz")
        assertThat(component.string()).isEqualTo("baz")
    }

    @Test
    fun nonNullableBindsInstance_failsOnNull() {
        try {
            DaggerFactoryBindsInstanceTest_BindsInstanceComponent.factory().create(null)
            fail()
        } catch (expected: NullPointerException) {
        }
    }

    @Target(
        AnnotationTarget.FUNCTION,
        AnnotationTarget.PROPERTY_GETTER,
        AnnotationTarget.PROPERTY_SETTER,
        AnnotationTarget.VALUE_PARAMETER
    )
    @Retention(RetentionPolicy.RUNTIME)
    internal annotation class Nullable

    @Component
    internal interface NullableBindsInstanceComponent {
        @Nullable
        fun string(): String?

        @Component.Factory
        interface Factory {
            fun create(@BindsInstance @Nullable string: String?): NullableBindsInstanceComponent?
        }
    }

    @Test
    fun nullableBindsInstance_doesNotFailOnNull() {
        val component: NullableBindsInstanceComponent =
            DaggerFactoryBindsInstanceTest_NullableBindsInstanceComponent.factory().create(null)
        assertThat(component.string()).isEqualTo(null)
    }

    @Component
    internal interface PrimitiveBindsInstanceComponent {
        val int: Int

        @Component.Factory
        interface Factory {
            fun create(@BindsInstance i: Int): PrimitiveBindsInstanceComponent?
        }
    }

    @Test
    fun primitiveBindsInstance() {
        val component: PrimitiveBindsInstanceComponent =
            DaggerFactoryBindsInstanceTest_PrimitiveBindsInstanceComponent.factory().create(1)
        assertThat(component.int).isEqualTo(1)
    }
}
