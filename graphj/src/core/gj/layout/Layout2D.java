/**
 * This file is part of GraphJ
 * 
 * Copyright (C) 2002-2004 Nils Meier
 * 
 * GraphJ is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * GraphJ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with GraphJ; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package gj.layout;

import java.awt.Shape;
import java.awt.geom.Point2D;

/**
 * A layout of a graph
 */
public interface Layout2D {

  /**
   * Graph's shape
   */
  public Shape getShapeOfGraph();

  /**
   * Graph's shape
   */
  public void setShapeOfGraph(Shape shape);

  /**
   * Edge's shape
   */
  public Shape getShapeOfEdge(Object vertexA, Object vertexB);

  /**
   * Edge's shape
   */
  public void setShapeOfEdge(Object vertexA, Object vertexB, Shape shape);

  /**
   * Vertex's shape
   */
  public Shape getShapeOfVertex(Object vertex);

  /**
   * Vertex's position
   */
  public Point2D getPositionOfVertex(Object vertex);

  /**
   * Vertex's position
   */
  public void setPositionOfVertex(Object vertex, Point2D pos);

} //Layout2D
