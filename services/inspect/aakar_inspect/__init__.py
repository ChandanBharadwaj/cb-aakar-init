"""Aakar inspect: Printability Report v1 + Print Estimate v1 (PLAN §7.9, §7.10).

Library first::

    from aakar_inspect import inspect_mesh, Constraints, SlicingSettings
    report, estimate = inspect_mesh(mesh, Constraints(), SlicingSettings())

FastAPI second: ``aakar_inspect.api:app`` (see openapi/inspect.v1.yaml).
"""

from .core import inspect_mesh
from .estimate import HeuristicEstimator, PrusaSlicerCli, SlicerBackend, SlicerError, default_backend, parse_gcode_footer
from .loaders import MeshLoadError, load_mesh, load_mesh_bytes, load_mesh_path, load_mesh_url
from .settings import Constraints, InvalidSettings, SlicingSettings

__all__ = [
    "Constraints",
    "HeuristicEstimator",
    "InvalidSettings",
    "MeshLoadError",
    "PrusaSlicerCli",
    "SlicerBackend",
    "SlicerError",
    "SlicingSettings",
    "default_backend",
    "inspect_mesh",
    "load_mesh",
    "load_mesh_bytes",
    "load_mesh_path",
    "load_mesh_url",
    "parse_gcode_footer",
]
