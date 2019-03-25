# Opencraft

This version of Glowstone was used for the Opencraft-Glowstone project by Christopher Esterhuyse as a continuation of previous works by Jerom van der Sar and Jesse Donkervliet in the same team. (Team page: https://www.atlarge-research.com/)

This repo contains a forked version of Glowstone, implementing a dynamic coninuous consistency mechanism intended to reduce work by manipulating client-server consistency whilst maintaining a known bound to inconsistency.

## Source changes

The implementation of the mechanism required additions and changes to the source. Namely:
1. `src/.../glowstone/entity/GlowPlayer`:
	1. Added imports
	2. `pulse` method performs new calls to Conit.
	3. `pulse` method performs new calls to YSCollector.
2. `src/.../glowstone/conits/`
	* A new directory with three new classes for the main work of the mechanism.
3. `src/.../GlowServer`:
	* Added imports
	* Added field for ConitConfig unit class.
	* Added initialization in constructor for ConitConfig
4. `src/.../GlowWorld`:
	* Added imports
	* Added calls to YSCollector
5. `src/.../scheduler/YSCollector`
	* A new class for interacting with Yardstick (adapted from the work of Jerom van der Sar "Yardstick Collector")
