package me.shika.di.render

import com.squareup.kotlinpoet.FileSpec
import me.shika.di.model.GraphNode

interface GraphRenderer : (GraphNode) -> FileSpec
